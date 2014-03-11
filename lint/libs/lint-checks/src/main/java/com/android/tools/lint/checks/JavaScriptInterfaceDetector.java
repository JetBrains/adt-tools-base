/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.google.common.collect.Maps;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks for add
 */
public class JavaScriptInterfaceDetector extends Detector implements Detector.ClassScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "JavascriptInterface", //$NON-NLS-1$
            "Missing @JavascriptInterface on methods",
            "Ensures that interfaces added with addJavascriptInterface are annotated with @JavascriptInterface",

            "As of API 17, you must annotate methods in objects registered with the " +
            "`addJavascriptInterface` method with a `@JavascriptInterface` annotation.",

            Category.SECURITY,
            8,
            Severity.ERROR,
            new Implementation(
                    JavaScriptInterfaceDetector.class,
                    Scope.CLASS_FILE_SCOPE))
            .addMoreInfo(
            "http://developer.android.com/reference/android/webkit/WebView.html#addJavascriptInterface(java.lang.Object, java.lang.String)"); //$NON-NLS-1$

    /** Constructs a new {@link JavaScriptInterfaceDetector} check */
    public JavaScriptInterfaceDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW; // because it relies on class loading referenced javascript interface
    }

    // ---- Implements ClassScanner ----


    @Nullable
    @Override
    public List<String> getApplicableCallOwners() {
        return Collections.singletonList("android/webkit/WebView"); //$NON-NLS-1$
    }

    @Override
    public void checkCall(@NonNull ClassContext context, @NonNull ClassNode classNode,
            @NonNull MethodNode method, @NonNull MethodInsnNode call) {
        if (context.getMainProject().getTargetSdk() < 17) {
            return;
        }

        if (!call.name.equals("addJavascriptInterface")) { //$NON-NLS-1$
            return;
        }

        if (context.getDriver().isSuppressed(ISSUE, classNode, method, call)) {
            return;
        }

        String type = findFirstArgType(context, classNode, method, call);
        if (type == null) {
            return;
        }

        // See if the object exposes any annotations
        ClassNode c = context.getDriver().findClass(context, type, 0);
        if (c != null) {
            if (c.methods.isEmpty()) {
                return;
            }

            while (true) {
                if (containsJavaScriptAnnotation(c)) {
                    return;
                }

                String owner = context.getDriver().getSuperClass(c.name);
                if (owner == null
                        || owner.startsWith("android/")   //$NON-NLS-1$
                        || owner.startsWith("java/")      //$NON-NLS-1$
                        || owner.startsWith("javax/")) {  //$NON-NLS-1$
                    break;
                }
                c = context.getDriver().findClass(context, owner, 0);
                if (c == null) {
                    break;
                }
            }

            Location location = context.getLocation(call);
            context.report(ISSUE, location,
                    "None of the methods in the added interface have been annotated "
                            + "with @android.webkit.JavascriptInterface; they will not "
                            + "be visible in API 17", null);
        }
    }

    private static boolean containsJavaScriptAnnotation(@NonNull ClassNode classNode) {
        List methodList = classNode.methods;
        for (Object om : methodList) {
            MethodNode m = (MethodNode) om;
            List annotations = m.visibleAnnotations;
            if (annotations != null) {
                for (Object oa : annotations) {
                    AnnotationNode a = (AnnotationNode) oa;
                    if (a.desc.equals("Landroid/webkit/JavascriptInterface;")) {  //$NON-NLS-1$
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    private static String findFirstArgType(ClassContext context, ClassNode classNode,
            MethodNode method, MethodInsnNode call) {
        // Find object being passed in as the first argument
        Analyzer analyzer = new Analyzer(new SourceInterpreter() {
            @Override
            public SourceValue newOperation(AbstractInsnNode insn) {
                if (insn.getOpcode() == Opcodes.NEW) {
                    String desc = ((TypeInsnNode) insn).desc;
                    return new TypeValue(1, desc);
                }
                return super.newOperation(insn);
            }

            @Override
            public SourceValue newValue(Type type) {
                if (type != null && type.getSort() == Type.VOID) {
                    return null;
                } else if (type != null) {
                    return new TypeValue(1, type.getInternalName());
                }
                return super.newValue(type);
            }

            @Override
            public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
                return value;
            }
        });
        try {
            Frame[] frames = analyzer.analyze(classNode.name, method);
            InsnList instructions = method.instructions;
            Frame frame = frames[instructions.indexOf(call)];
            if (frame.getStackSize() <= 1) {
                return null;
            }
            SourceValue stackValue = (SourceValue) frame.getStack(1);
            if (stackValue instanceof TypeValue) {
                return ((TypeValue) stackValue).getFqcn();
            }
        } catch (AnalyzerException e) {
            context.log(e, null);
        }

        return null;
    }

    private static class TypeValue extends SourceValue {
        private final String mFqcn;

        TypeValue(int size, String fqcn) {
            super(size);
            mFqcn = fqcn;
        }

        String getFqcn() {
            return mFqcn;
        }

        @Override
        public int getSize() {
            return 1;
        }
    }
}
