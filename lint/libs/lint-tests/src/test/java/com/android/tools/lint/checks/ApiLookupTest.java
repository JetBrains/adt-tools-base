/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.DOT_AAR;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.ide.common.repository.SdkMavenRepository.ANDROID;
import static com.android.tools.lint.detector.api.LintUtils.getChildren;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.repository.io.FileOpUtils;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

@SuppressWarnings({"javadoc", "ConstantConditions"})
public class ApiLookupTest extends AbstractCheckTest {
    private final ApiLookup mDb = ApiLookup.get(new TestLintClient());

    public void test1() {
        assertEquals(5, mDb.getFieldVersion("android/Manifest$permission", "AUTHENTICATE_ACCOUNTS"));
        assertTrue(mDb.getFieldVersion("android/R$attr", "absListViewStyle") <= 1);
        assertEquals(11, mDb.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertEquals(5, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        assertEquals(4, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "setTargetDensity", "(Landroid/util/DisplayMetrics;)V"));
        assertEquals(7, mDb.getClassVersion("android/app/WallpaperInfo"));
        assertEquals(11, mDb.getClassVersion("android/widget/StackView"));
        assertTrue(mDb.getClassVersion("ava/text/ChoiceFormat") <= 1);

        // Class lookup: Unknown class
        assertEquals(-1, mDb.getClassVersion("foo/Bar"));
        // Field lookup: Unknown class
        assertEquals(-1, mDb.getFieldVersion("foo/Bar", "FOOBAR"));
        // Field lookup: Unknown field
        assertEquals(-1, mDb.getFieldVersion("android/Manifest$permission", "FOOBAR"));
        // Method lookup: Unknown class
        assertEquals(-1, mDb.getCallVersion("foo/Bar",
                "<init>", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown name
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "foo", "(Landroid/content/res/Resources;Ljava/lang/String;)V"));
        // Method lookup: Unknown argument list
        assertEquals(-1, mDb.getCallVersion("android/graphics/drawable/BitmapDrawable",
                "<init>", "(I)V"));
    }

    public void test2() {
        // Regression test:
        // This used to return 11 because of some wildcard syntax in the signature
        assertTrue(mDb.getCallVersion("java/lang/Object", "getClass", "()") <= 1);
    }

    public void testIssue26467() {
        assertTrue(mDb.getCallVersion("java/nio/ByteBuffer", "array", "()") <= 1);
        assertEquals(9, mDb.getCallVersion("java/nio/Buffer", "array", "()"));
    }

    public void testNoInheritedConstructors() {
        assertTrue(mDb.getCallVersion("java/util/zip/ZipOutputStream", "<init>", "()") <= 1);
        assertTrue(mDb.getCallVersion("android/app/AliasActivity", "<init>", "(Landroid/content/Context;I)") <= 1);
    }

    public void testIssue35190() {
        assertEquals(9, mDb.getCallVersion("java/io/IOException", "<init>",
                "(Ljava/lang/Throwable;)V"));
    }

    public void testDeprecatedFields() {
        // Not deprecated:
        assertEquals(-1, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_PACKAGE_SIZE"));
        // Field only has since > 1, no deprecation
        assertEquals(9, mDb.getFieldVersion("android/Manifest$permission", "NFC"));

        // Deprecated
        assertEquals(21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "GET_TASKS"));
        // Field both deprecated and since > 1
        assertEquals(21, mDb.getFieldDeprecatedIn("android/Manifest$permission", "READ_SOCIAL_STREAM"));
        assertEquals(15, mDb.getFieldVersion("android/Manifest$permission", "READ_SOCIAL_STREAM"));
    }

    public void testDeprecatedCalls() {
        // Not deprecated:
        //assertEquals(12, mDb.getCallVersion("android/app/Fragment", "onInflate",
        //        "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        assertEquals(23, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate",
                "(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        assertEquals(-1, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate",
                "(Landroid/content/Context;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
        // Deprecated
        assertEquals(16, mDb.getCallDeprecatedIn("android/app/Service", "onStart", "(Landroid/content/Intent;I)V"));
        assertEquals(16, mDb.getCallDeprecatedIn("android/app/Fragment", "onInflate", "(Landroid/util/AttributeSet;Landroid/os/Bundle;)V"));
    }

    public void testDeprecatedClasses() {
        // Not deprecated:
        assertEquals(-1, mDb.getClassDeprecatedIn("android/app/Fragment"));
        // Deprecated
        assertEquals(9, mDb.getClassDeprecatedIn("org/xml/sax/Parser"));
    }

    public void testInheritInterfaces() {
        // The onPreferenceStartFragment is inherited via the
        // android/preference/PreferenceFragment$OnPreferenceStartFragmentCallback
        // interface
        assertEquals(11, mDb.getCallVersion("android/preference/PreferenceActivity",
                "onPreferenceStartFragment",
                "(Landroid/preference/PreferenceFragment;Landroid/preference/Preference;)"));
    }

    public void testInterfaceApi() {
        assertEquals(21, mDb.getClassVersion("android/animation/StateListAnimator"));
        assertEquals(11, mDb.getValidCastVersion("android/animation/AnimatorListenerAdapter",
                "android/animation/Animator$AnimatorListener"));
        assertEquals(19, mDb.getValidCastVersion("android/animation/AnimatorListenerAdapter",
                "android/animation/Animator$AnimatorPauseListener"));

        assertEquals(11, mDb.getValidCastVersion("android/animation/Animator",
                "java/lang/Cloneable"));
        assertEquals(22, mDb.getValidCastVersion("android/animation/StateListAnimator",
                "java/lang/Cloneable"));
    }

    public void testSuperClassCast() {
        assertEquals(22, mDb.getValidCastVersion("android/view/animation/AccelerateDecelerateInterpolator",
                "android/view/animation/BaseInterpolator"));
    }

    public void testIsValidPackage() {
        assertTrue(mDb.isValidJavaPackage("java/lang/Integer"));
        assertTrue(mDb.isValidJavaPackage("javax/crypto/Cipher"));
        assertTrue(mDb.isValidJavaPackage("java/awt/font/NumericShaper"));

        assertFalse(mDb.isValidJavaPackage("javax/swing/JButton"));
        assertFalse(mDb.isValidJavaPackage("java/rmi/Naming"));
        assertFalse(mDb.isValidJavaPackage("java/lang/instrument/Instrumentation"));
    }

    @Override
    protected Detector getDetector() {
        fail("This is not used in the ApiDatabase test");
        return null;
    }

    private File mCacheDir;
    @SuppressWarnings("StringBufferField")
    private StringBuilder mLogBuffer = new StringBuilder();

    @SuppressWarnings({"ConstantConditions", "IOResourceOpenedButNotSafelyClosed",
            "ResultOfMethodCallIgnored"})
    public void testCorruptedCacheHandling() throws Exception {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println("Skipping " + getName() + ": not valid while regenerating indices");
            return;
        }

        ApiLookup lookup;

        // Real cache:
        mCacheDir = new TestLintClient().getCacheDir(true);
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Custom cache dir: should also work
        mCacheDir = new File(getTempDir(), "test-cache");
        mCacheDir.mkdirs();
        mLogBuffer.setLength(0);
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();

        // Now truncate cache file
        File cacheFile = new File(mCacheDir,
                ApiLookup.getCacheFileName("api-versions.xml",
                        ApiLookup.getPlatformVersion(new LookupTestClient()))); //$NON-NLS-1$
        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half
        raf.setLength(100);  // Broken header
        raf.close();
        ApiLookup.get(new LookupTestClient());
        String message = mLogBuffer.toString();
        // NOTE: This test is incompatible with the DEBUG_FORCE_REGENERATE_BINARY and WRITE_STATS
        // flags in the ApiLookup class, so if the test fails during development and those are
        // set, clear them.
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file in half in the data portion
        raf.setLength(raf.length() / 2);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        // This data is now truncated: lookup returns the wrong size.
        try {
            assertNotNull(lookup);
            lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance");
            fail("Can't look up bogus data");
        } catch (Throwable t) {
            // Expected this: the database is corrupted.
        }
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"));
        assertTrue(message.contains(mCacheDir.getPath()));
        ApiLookup.dispose();

        mLogBuffer.setLength(0);
        assertTrue(cacheFile.exists());
        raf = new RandomAccessFile(cacheFile, "rw");
        // Truncate file to 0 bytes
        raf.setLength(0);
        raf.close();
        lookup = ApiLookup.get(new LookupTestClient());
        assertEquals(11, lookup.getFieldVersion("android/R$attr", "actionMenuTextAppearance"));
        assertNotNull(lookup);
        assertEquals("", mLogBuffer.toString()); // No warnings
        ApiLookup.dispose();
    }

    private static final boolean CHECK_DEPRECATED = true;

    private static void assertSameApi(String desc, int expected, int actual) {
        // In the database we don't distinguish between 1 and -1 (to save diskspace)
        if (expected <= 1) {
            expected = -1;
        }
        if (actual <= 1) {
            actual = -1;
        }
        assertEquals(desc, expected, actual);
    }

    public void testFindEverything() throws Exception {
        // Load the API versions file and look up every single method/field/class in there
        // (provided since != 1) and also check the deprecated calls.

        File file = createClient().findResource("platform-tools/api/api-versions.xml");
        if (file == null || !file.exists()) {
            return;
        }

        Api info = Api.parseApi(file);
        assertNotNull(info);
        for (ApiClass cls : info.getClasses().values()) {
            int classSince = cls.getSince();
            String className = cls.getName();
            if (className.startsWith("android/support/")) {
                continue;
            }
            assertSameApi(className, classSince, mDb.getClassVersion(className));

            for (String method : cls.getAllMethods(info)) {
                int since = cls.getMethod(method, info);
                int index = method.indexOf('(');
                String name = method.substring(0, index);
                String desc = method.substring(index);
                assertSameApi(method, since, mDb.getCallVersion(className, name, desc));

            }
            for (String method : cls.getAllFields(info)) {
                int since = cls.getField(method, info);
                assertSameApi(method, since, mDb.getFieldVersion(className, method));
            }

            for (Pair<String, Integer> pair : cls.getInterfaces()) {
                String interfaceName = pair.getFirst();
                int api = pair.getSecond();
                assertSameApi(interfaceName, api,
                        mDb.getValidCastVersion(className, interfaceName));
            }
        }

        if (CHECK_DEPRECATED) {
            for (ApiClass cls : info.getClasses().values()) {
                int classDeprecatedIn = cls.getDeprecatedIn();
                String className = cls.getName();
                if (className.startsWith("android/support/")) {
                    continue;
                }
                if (classDeprecatedIn > 1) {
                    assertSameApi(className, classDeprecatedIn,
                            mDb.getClassDeprecatedIn(className));
                } else {
                    assertSameApi(className, -1, mDb.getClassDeprecatedIn(className));
                }

                for (String method : cls.getAllMethods(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    int index = method.indexOf('(');
                    String name = method.substring(0, index);
                    String desc = method.substring(index);
                    assertSameApi(method + " in " + className, deprecatedIn,
                            mDb.getCallDeprecatedIn(className, name, desc));
                }
                for (String method : cls.getAllFields(info)) {
                    int deprecatedIn = cls.getMemberDeprecatedIn(method, info);
                    assertSameApi(method, deprecatedIn, mDb.getFieldDeprecatedIn(className,
                            method));
                }
            }
        }
    }

    public void testLookUpContractSettings() {
        assertEquals(14, mDb.getFieldVersion("android/provider/ContactsContract$Settings", "DATA_SET"));
    }

    public void testIssue196925() {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            // This test doesn't work when regenerating binaries: it's tied to data
            // not included in api-versions.xml
            return;
        }
        //196925: Incorrect Lint NewApi error on FloatingActionButton#setBackgroundTintList()
        assertEquals(7, mDb.getCallVersion("android/support/design/widget/FloatingActionButton",
                "getBackgroundTintList", "()"));
        assertEquals(7, mDb.getCallVersion("android/support/design/widget/FloatingActionButton",
                "setBackgroundTintList", "(Landroid/content/res/ColorStateList;)"));
    }

    private final class LookupTestClient extends TestLintClient {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public File getCacheDir(boolean create) {
            assertNotNull(mCacheDir);
            if (create && !mCacheDir.exists()) {
                mCacheDir.mkdirs();
            }
            return mCacheDir;
        }

        @Override
        public void log(
                @NonNull Severity severity,
                @Nullable Throwable exception,
                @Nullable String format,
                @Nullable Object... args) {
            if (format != null) {
                mLogBuffer.append(String.format(format, args));
                mLogBuffer.append('\n');
            }
            if (exception != null) {
                StringWriter writer = new StringWriter();
                exception.printStackTrace(new PrintWriter(writer));
                mLogBuffer.append(writer.toString());
                mLogBuffer.append('\n');
            }
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            log(Severity.WARNING, exception, format, args);
        }
    }

    /**
     * Finds the most recent version of the support/appcompat library, and for any
     * classes that extend framework classes, creates a list of APIs that should
     * <b>not</b> be flagged when called via the support library (since the support
     * library provides a backport of the APIs).
     * <p>
     * Example: {@code FloatingActionButton#setBackgroundTintList()}
     * This method is available on any version, yet it extends a method
     * ({@code ImageButton#setBackgroundTintList} which has min api 21) so lint
     * flags it.
     */
    public void testSupportLibraryMap() throws Exception {
        if (ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            generateSupportLibraryFile();
        }
    }

    @SuppressWarnings("unchecked")
    private void generateSupportLibraryFile() throws Exception {
        //noinspection PointlessBooleanExpression
        if (!ApiLookup.DEBUG_FORCE_REGENERATE_BINARY) {
            System.out.println("Ignoring " + getName() + " since"
                    + " ApiLookup.DEBUG_FORCE_REGENERATE_BINARY is not set to true");
            return;
        }
        File sdkHome = createClient().getSdkHome();
        if (sdkHome == null) {
            System.err.println("Ignoring " + getName() + ": no SDK home found");
            return;
        }

        File root = ANDROID.getRepositoryLocation(sdkHome, true, FileOpUtils.create());
        if (root == null) {
            System.out.println("No android support repository installed in the SDK home");
            return;
        }

        @SuppressWarnings("SpellCheckingInspection")
        String[] artifacts = new String[] {
                "appcompat-v7",
                "cardview-v7",
                "customtabs",
                "design",
                "gridlayout-v7",
                "leanback-v17",
                "mediarouter-v7",
                "multidex",
                "multidex-instrumentation",
                "palette-v7",
                "percent",
                "preference-leanback-v17",
                "preference-v14",
                "preference-v7",
                "recommendation",
                "recyclerview-v7",
                "support-annotations",
                "support-v13",
                "support-v4",
//                "test"
        };
        String groupId = "com.android.support";

        Map<String, ClassNode> classes = Maps.newHashMapWithExpectedSize(1000);
        Map<String, Integer> minSdkMap = Maps.newHashMapWithExpectedSize(1000);

        for (String artifact : artifacts) {
            GradleCoordinate version = ANDROID.getHighestInstalledVersion(sdkHome, groupId,
                    artifact, null, true, FileOpUtils.create());
            String revision = version.getRevision();
            File file = new File(root, groupId.replace('.', separatorChar) + separatorChar
                    + artifact + separatorChar + revision
                    + separatorChar + artifact + "-" + revision + DOT_AAR);
            if (!file.exists()) {
                String path = file.getPath();
                path = path.substring(0, path.length() - DOT_AAR.length()) + DOT_JAR;
                file = new File(path);
                if (!file.exists()) {
                    System.err.println(
                            "Ignoring artifact " + artifact + ": couldn't find .aar/.jar file");
                    continue;
                }
            }

            System.out.println("Analyzing file " + file);

            byte[] bytes = Files.toByteArray(file);
            String path = file.getPath();
            if (path.endsWith(DOT_AAR)) {
                analyzeAar(bytes, classes, minSdkMap);
            } else {
                assertTrue(path, path.endsWith(DOT_JAR));
                analyzeJar(bytes, classes, minSdkMap, -1);
            }
        }

        System.out.println("Found " + classes.size() + " classes (including innerclasses)");
        File file = createClient().findResource("platform-tools/api/api-versions.xml");
        if (file == null || !file.exists()) {
            System.out.println("No API versions xml file found.");
            return;
        }

        Api api = Api.parseApi(file);

        Document document = XmlUtils.parseDocument(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!--\n"
                + "  ~ Copyright (C) 2015 The Android Open Source Project\n"
                + "  ~\n"
                + "  ~ Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                + "  ~ you may not use this file except in compliance with the License.\n"
                + "  ~ You may obtain a copy of the License at\n"
                + "  ~\n"
                + "  ~      http://www.apache.org/licenses/LICENSE-2.0\n"
                + "  ~\n"
                + "  ~ Unless required by applicable law or agreed to in writing, software\n"
                + "  ~ distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                + "  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                + "  ~ See the License for the specific language governing permissions and\n"
                + "  ~ limitations under the License.\n"
                + "  -->\n"
                + "<!-- This file is generated by ApiLookupTest#generateSupportLibraryFile() -->\n"
                + "<api version=\"2\"/>", false);
        Element rootElement = document.getDocumentElement();

        Set<ClassNode> referencedClasses = Sets.newHashSetWithExpectedSize(100);
        Set<ClassNode> referencedSuperClasses = Sets.newHashSetWithExpectedSize(100);

        // Walk through the various support classes, and walk up the inheritance chain
        // to see if it extends a class from the support library, and if so, mark
        // any methods as deliberately having a lower API level
        for (ClassNode node : sorted(classes.values())) {
            String name = node.name;
            if (name.indexOf('$') != -1) {
                // Ignore inner classes
                continue;
            }
            if ((node.access & Modifier.PUBLIC) == 0) {
                continue;
            }
            ApiClass apiClass = extendsKnownApi(api, node, classes);
            if (apiClass != null && !apiClass.getName().equals("java/lang/Object")) {
                @SuppressWarnings("unchecked") // ASM API
                List<MethodNode> methodList = sorted((List<MethodNode>) node.methods);
                if (methodList.isEmpty()) {
                    return;
                }

                int supportMin = getMinSdk(node.name, minSdkMap);
                Element classNode = null;

                for (MethodNode method : methodList) {
                    String signature = method.name + method.desc;
                    int end = signature.indexOf(')');
                    if (end != -1) {
                        signature = signature.substring(0, end + 1);
                    }
                    int methodSince = apiClass.getMethod(signature, api);
                    if (methodSince < Integer.MAX_VALUE) {
                        if (supportMin < methodSince) {
                            referencedClasses.add(node);
                            if (classNode == null) {
                                classNode = document.createElement("class");
                                rootElement.appendChild(classNode);
                                classNode.setAttribute("name", node.name);
                                classNode.setAttribute("since", Integer.toString(supportMin));
                                if (node.superName != null) {
                                    Element extendsNode = document.createElement("extends");
                                    classNode.appendChild(extendsNode);
                                    extendsNode.setAttribute("name", node.superName);

                                    ClassNode superClassNode = classes.get(node.superName);
                                    while (superClassNode != null) {
                                        referencedSuperClasses.add(superClassNode);
                                        superClassNode = classes.get(superClassNode.superName);
                                    }
                                }
                            }
                            Element methodNode = document.createElement("method");
                            classNode.appendChild(methodNode);
                            methodNode.setAttribute("name", method.name + method.desc);
                            methodNode.setAttribute("since", Integer.toString(supportMin));
                        }
                    }
                }
            }
        }

        // Also list any super classes referenced such that we ensure we have super-class
        // references to them in the ApiClass info (such that it can correctly pull in
        // methods from the framework to check their since-versions relative to the class'
        // own since value)
        referencedSuperClasses.removeAll(referencedClasses);
        if (!referencedSuperClasses.isEmpty()) {
            rootElement.appendChild(document.createTextNode("\n"));
            rootElement.appendChild(document.createComment("Referenced Super Classes"));
            for (ClassNode node : sorted(referencedSuperClasses)) {
                int supportMin = getMinSdk(node.name, minSdkMap);
                Element classNode = document.createElement("class");
                rootElement.appendChild(classNode);
                classNode.setAttribute("name", node.name);
                classNode.setAttribute("since", Integer.toString(supportMin));
                if (node.superName != null) {
                    Element extendsNode = document.createElement("extends");
                    classNode.appendChild(extendsNode);
                    extendsNode.setAttribute("name", node.superName);
                }
            }
        }

        String xml = XmlPrettyPrinter.prettyPrint(document, XmlFormatPreferences.defaults(),
                XmlFormatStyle.RESOURCE, "\n", false);
        xml = xml.replace("\n\n", "\n");

        File xmlFile = findSrcDir();
        if (xmlFile == null) {
            System.out.println("Ignoring " + getName() + ": Should set $ANDROID_SRC to point "
                    + "to source dir to run this test");
            return;
        }
        xmlFile = new File(xmlFile, ("tools/base/lint/libs/lint-checks/src/main/java/com/android/"
                + "tools/lint/checks/api-versions-support-library.xml").replace('/', separatorChar));
        assertTrue(xmlFile.getPath(), xmlFile.exists());
        String prev = Files.toString(xmlFile, UTF_8);
        assertEquals(prev, xml);
    }

    @NonNull
    private static List<MethodNode> sorted(List<MethodNode> methods) {
        List<MethodNode> sorted = Lists.newArrayList(methods);
        Collections.sort(sorted, new Comparator<MethodNode>() {
            @Override
            public int compare(MethodNode node1, MethodNode node2) {
                int delta = node1.name.compareTo(node2.name);
                if (delta != 0) {
                    return delta;
                }
                return node1.desc.compareTo(node2.desc);
            }
        });
        return sorted;
    }

    @NonNull
    private static List<ClassNode> sorted(Collection<ClassNode> classes) {
        List<ClassNode> sorted = Lists.newArrayList(classes);
        Collections.sort(sorted, new Comparator<ClassNode>() {
            @Override
            public int compare(ClassNode node1, ClassNode node2) {
                return node1.name.compareTo(node2.name);
            }
        });
        return sorted;
    }

    private static int getMinSdk(@NonNull String name, @NonNull Map<String, Integer> minSdkMap) {
        Integer min = minSdkMap.get(name);
        if (min != null) {
            return min;
        }
        String prefix = "android/support/v";
        if (name.startsWith(prefix)) {
            int endIndex = name.indexOf('/', prefix.length());
            if (endIndex != -1) {
                return Integer.parseInt(name.substring(prefix.length(), endIndex));
            }
        }

        return 7;
    }

    @Nullable
    private static ClassNode getSuperClass(@NonNull ClassNode node,
            @NonNull Map<String, ClassNode> classes) {
        if (node.superName != null) {
            return classes.get(node.superName);
        }

        return null;
    }

    @Nullable
    private static ApiClass extendsKnownApi(@NonNull Api api, @Nullable ClassNode node,
            @NonNull Map<String, ClassNode> classes) {
        while (node != null) {
            ApiClass cls = api.getClass(node.name);
            if (cls != null) {
                return cls;
            }

            ClassNode superClass = getSuperClass(node, classes);
            if (superClass == null && node.superName != null) {
                // Pointing up into android.jar, not in our class map?
                return api.getClass(node.superName);
            } else {
                node = superClass;
            }
        }

        return null;
    }

    private static void analyzeAar(@NonNull byte[] bytes, @NonNull Map<String, ClassNode> classes,
            @NonNull Map<String, Integer> minSdkMap) throws Exception {
        JarInputStream zis = null;
        try {
            InputStream fis = new ByteArrayInputStream(bytes);
            try {
                zis = new JarInputStream(fis);
                ZipEntry entry = zis.getNextEntry();
                int minSdk = -1;
                while (entry != null) {
                    String name = entry.getName();
                    if (name.equals(ANDROID_MANIFEST_XML)) {
                        byte[] b = ByteStreams.toByteArray(zis);
                        assertNotNull(b);
                        String xml = new String(b, UTF_8);
                        Document document = XmlUtils.parseDocumentSilently(xml, true);
                        assertNotNull(document);
                        assertNotNull(document.getDocumentElement());
                        for (Element element : getChildren(document.getDocumentElement())) {
                            if (element.getTagName().equals(TAG_USES_SDK)) {
                                String min = element.getAttributeNS(ANDROID_URI,
                                        ATTR_MIN_SDK_VERSION);
                                if (!min.isEmpty()) {
                                    try {
                                        minSdk = Integer.parseInt(min);
                                    } catch (NumberFormatException e) {
                                        fail(e.toString());
                                    }
                                }
                            }
                        }
                    } else if (name.equals(FN_CLASSES_JAR)) {
                        // Bingo!
                        byte[] b = ByteStreams.toByteArray(zis);
                        assertNotNull(b);
                        analyzeJar(b, classes, minSdkMap, minSdk);
                        break;
                    }
                    entry = zis.getNextEntry();
                }
            } finally {
                Closeables.close(fis, true);
            }
        } finally {
            Closeables.close(zis, false);
        }
    }

    private static void analyzeJar(@NonNull byte[] bytes, @NonNull Map<String, ClassNode> classes,
            @NonNull Map<String, Integer> minSdkMap, int manifestMinSdk) throws Exception {
        JarInputStream zis = null;
        try {
            InputStream fis = new ByteArrayInputStream(bytes);
            try {
                zis = new JarInputStream(fis);
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    if (name.endsWith(DOT_CLASS)) {
                        // Bingo!
                        byte[] b = ByteStreams.toByteArray(zis);
                        if (b != null) {
                            analyzeClass(b, classes, minSdkMap, manifestMinSdk);
                        }
                    }
                    entry = zis.getNextEntry();
                }
            } finally {
                Closeables.close(fis, true);
            }
        } finally {
            Closeables.close(zis, false);
        }
    }

    private static void analyzeClass(@NonNull byte[] bytes,
            @NonNull Map<String, ClassNode> classes, @NonNull Map<String, Integer> minSdkMap,
            int manifestMinSdk) {

        ClassReader reader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0 /* flags */);

        assertNull(classes.get(classNode.name));
        classes.put(classNode.name, classNode);

        int minSdk = manifestMinSdk != -1 ? manifestMinSdk : getMinSdk(classNode.name, minSdkMap);
        minSdkMap.put(classNode.name, minSdk);
    }
}
