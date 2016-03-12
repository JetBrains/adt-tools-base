/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks to detect vulnerable versions of Apache Cordova.
 */
public class CordovaVersionDetector extends Detector implements Detector.ClassScanner {

    private static final Implementation IMPL = new Implementation(
            CordovaVersionDetector.class,
            EnumSet.of(Scope.CLASS_FILE, Scope.JAVA_LIBRARIES));

    /** Vulnerable Cordova Version */
    public static final Issue ISSUE = Issue.create(
            "VulnerableCordovaVersion",
            "Vulnerable Cordova Version",
            "The version of Cordova used in the app is vulnerable to security " +
            "issues. Please update to the latest Apache Cordova version.",
            Category.SECURITY,
            9,
            Severity.WARNING,
            IMPL)
            .addMoreInfo("https://cordova.apache.org/announcements/2015/11/20/security.html");

    // All cordova versions below 4.1.1 are considered vulnerable.
    private static final GradleVersion VALID_CORDOVA_VERSION =
            GradleVersion.parse("4.1.1");
    /** Version string format in a class file. Note that any qualifiers such as -dev are ignored.*/
    private static final Pattern VERSION_STR = Pattern.compile("(\\d+\\.\\d+\\.\\d+).*");
    /** The class name that declares the cordovaVersion for versions < 3.x.x */
    private static final String FQN_CORDOVA_DEVICE = "org/apache/cordova/Device"; //$NON-NLS-1$
    /** Class name that declares CORDOVA_VERSION static field for versions >= 3.x.x */
    /** The name of the static field if present in the {@code FQN_CORDOVA_DEVICE} */
    private static final String FIELD_NAME_CORDOVA_VERSION = "cordovaVersion"; //$NON-NLS-1$

    private static final String FQN_CORDOVA_WEBVIEW =
            "org/apache/cordova/CordovaWebView"; //$NON-NLS-1$
    /** The name of the static final field present in {@code FQN_CORDOVA_WEBVIEW} */
    private static final String FIELD_NAME_CORDOVA_VERSION_WEBVIEW =
            "CORDOVA_VERSION"; //$NON-NLS-1$

    private static final String CORDOVA_DOT_JS = "cordova.js"; //$NON-NLS-1$

    private static final FilenameFilter CORDOVA_JS_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            return filename.startsWith(CORDOVA_DOT_JS) || new File(dir, filename).isDirectory();
        }
    };

    /** Constructs a new {@link CordovaVersionDetector} check */
    public CordovaVersionDetector() {
    }

    /**
     * The cordova version is similar to a dewey decimal like system
     * used by {@link GradleVersion} except for the named qualifiers.
     */
    private GradleVersion mCordovaVersion;

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.NORMAL;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        checkAssetsFolder(context);
    }

    private void checkAssetsFolder(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            // If this is a library project not being analyzed, ignore it
            return;
        }
        Project project = context.getProject();
        List<File> assetFolders = project.getAssetFolders();
        Deque<File> files = new ArrayDeque<File>();
        for (File assetFolder : assetFolders) {
            // The js file could be at an arbitrary level in the directory tree.
            // examples:
            // assets/www/cordova.js
            // assets/www/plugins/phonegap/cordova.js.2.2.android
            files.push(assetFolder);
            while (!files.isEmpty() && mCordovaVersion == null) {
                File current = files.pop();
                if (current.isDirectory()) {
                    File[] filtered = current.listFiles(CORDOVA_JS_FILTER);
                    if (filtered != null) {
                        for (File file : filtered) {
                            files.push(file);
                        }
                    }
                } else {
                    checkFile(context, current);
                }
            }
            files.clear();
        }
    }

    private void checkFile(@NonNull Context context, @NonNull File file) {
        if (mCordovaVersion == null
                && file.getPath().contains(CORDOVA_DOT_JS)
                && file.getName().startsWith(CORDOVA_DOT_JS)) {
            try {
                mCordovaVersion = Files.readLines(
                        file, Charsets.UTF_8, new JsVersionLineProcessor());
                if (mCordovaVersion != null) {
                    validateCordovaVersion(context, mCordovaVersion, Location.create(file));
                }
            } catch (IOException ignore) {
                // Ignore this exception.
            }
        }
    }

    private static void validateCordovaVersion(@NonNull Context context,
            @NonNull GradleVersion cordovaVersion, Location location) {
        if (cordovaVersion.compareIgnoringQualifiers(VALID_CORDOVA_VERSION) < 0) {
            String message = String.format(
                    "You are using a vulnerable version of Cordova: %1$s",
                    cordovaVersion.toString());
            context.report(ISSUE, location, message);
        }
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        //noinspection VariableNotUsedInsideIf
        if (mCordovaVersion != null) {
            // Exit early if we have already found the cordova version in the JS file.
            // This will be the case for all versions > 3.x.x
            return;
        }

        // For cordova versions such as 2.7.1, the version is a static *non-final* field in
        // a class named Device. Since it is non-final, it is initialized in the <clinit> method.
        // Example:
        //
        // ldc           #5                  // String 2.7.1
        // putstatic     #6                  // Field cordovaVersion:Ljava/lang/String;
        // ...
        if (classNode.name.equals(FQN_CORDOVA_DEVICE)) {
            //noinspection unchecked ASM api.
            List<MethodNode> methods = classNode.methods;
            for (MethodNode method : methods) {
                if (SdkConstants.CLASS_CONSTRUCTOR.equals(method.name)) {
                    InsnList nodes = method.instructions;
                    for (int i = 0, n = nodes.size(); i < n; i++) {
                        AbstractInsnNode instruction = nodes.get(i);
                        int type = instruction.getType();
                        if (type == AbstractInsnNode.FIELD_INSN) {
                            checkInstructionInternal(context, classNode, instruction);
                            break;
                        }
                    }
                }
            }
        } else if (classNode.name.equals(FQN_CORDOVA_WEBVIEW)) {
            // For versions > 3.x.x, the version string is stored as a static final String in
            // CordovaWebView.
            // Note that this is also stored in the cordova.js.* but from a lint api perspective,
            // it's much faster to look it up here than load and check in the JS file.
            // e.g.
            //   public static final java.lang.String CORDOVA_VERSION;
            //      descriptor: Ljava/lang/String;
            //      flags: ACC_PUBLIC, ACC_STATIC, ACC_FINAL
            //      ConstantValue: String 4.1.1
            //
            //noinspection unchecked ASM api.
            List<FieldNode> fields = classNode.fields;
            for (FieldNode node : fields) {
                if (FIELD_NAME_CORDOVA_VERSION_WEBVIEW.equals(node.name)
                        && (node.access & Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL // is final
                        && (node.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC // is static
                        && node.value instanceof String) {
                    mCordovaVersion = createVersion((String) node.value);
                    if (mCordovaVersion != null) {
                        validateCordovaVersion(context, mCordovaVersion,
                                context.getLocation(classNode));
                    }
                }
            }
        }
    }

    private void checkInstructionInternal(ClassContext context, ClassNode classNode,
            AbstractInsnNode instruction) {

        FieldInsnNode node = (FieldInsnNode) instruction;
        if (node.getOpcode() == Opcodes.PUTSTATIC
                && node.owner.equals(FQN_CORDOVA_DEVICE)
                && node.name.equals(FIELD_NAME_CORDOVA_VERSION)) {
            AbstractInsnNode prevInstruction = LintUtils.getPrevInstruction(node);
            if (prevInstruction == null || prevInstruction.getOpcode() != Opcodes.LDC) {
                return;
            }
            LdcInsnNode ldcInsnNode = (LdcInsnNode) prevInstruction;
            if (ldcInsnNode.cst instanceof String) {
                mCordovaVersion = createVersion((String) ldcInsnNode.cst);
                if (mCordovaVersion != null) {
                    validateCordovaVersion(context, mCordovaVersion,
                            context.getLocation(classNode));
                }
            }
        }
    }

    private static GradleVersion createVersion(String version) {
        Matcher matcher = VERSION_STR.matcher(version);
        if (matcher.matches()) {
            return GradleVersion.tryParse(matcher.group(1));
        }
        return null;
    }

    /**
     * A {@link LineProcessor} that matches a specific cordova pattern and if successful terminates
     * early. The constant is typically declared at the start of the file so it makes it efficient
     * to terminate early.
     */
    public static class JsVersionLineProcessor implements LineProcessor<GradleVersion> {

        // var CORDOVA_JS_BUILD_LABEL = '3.5.1-dev';
        private static final Pattern PATTERN = Pattern.compile(
                "var\\s*(PLATFORM_VERSION_BUILD_LABEL|CORDOVA_JS_BUILD_LABEL)\\s*"
                        + "=\\s*'(\\d+\\.\\d+\\.\\d+)[^']*';.*");

        private GradleVersion mVersion;

        @Override
        public boolean processLine(@NonNull String line) throws IOException {
            Matcher matcher = PATTERN.matcher(line);
            if (matcher.matches()) {
                mVersion = GradleVersion.tryParse(matcher.group(2));
                return false; // stop processing other lines.
            }
            return true;
        }

        @Override
        public GradleVersion getResult() {
            return mVersion;
        }
    }
}
