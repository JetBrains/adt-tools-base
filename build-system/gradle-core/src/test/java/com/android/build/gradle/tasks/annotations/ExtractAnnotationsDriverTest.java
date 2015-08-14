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

package com.android.build.gradle.tasks.annotations;

import static com.android.utils.SdkUtils.fileToUrlString;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.SdkTestCase;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

public class ExtractAnnotationsDriverTest extends SdkTestCase {
    private static boolean isValidEcj() {
        // When this test is run from within Gradle's testing infrastructure, e.g.
        // via "./gradlew :base:gradle-core:test", Gradle's own *internal* dependencies
        // somehow end up on the classpath, and in particular, an ancient version of
        // (ECJ (3.x)) take priority over our explicit lint dependency which should
        // bring in ECJ 4.
        //
        // This causes the test to fail. For now, make the test only run when a valid
        // ECJ is present.
        try {
            CompilerOptions.class.getField("originalComplianceLevel");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void testProGuard() throws Exception {
        if (!isValidEcj()) {
            return;
        }

        File androidJar = findAndroidJar(false);
        if (androidJar == null) {
            System.err.println("Skipping test " + this.getClass() + "#" + getName()
                    + ": No android.jar found");
            return;
        }

        File project = createProject(mKeepTest, mKeepAnnotation);

        File output = File.createTempFile("proguard", ".cfg");
        output.deleteOnExit();

        List<String> list = java.util.Arrays.asList(
                "--sources",
                new File(project, "src").getPath(),
                "--classpath",
                androidJar.getPath(),

                "--quiet",
                "--language-level",
                "1.6",
                "--proguard",
                output.getPath()
        );
        String[] args = list.toArray(new String[list.size()]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);
        assertEquals(""
                        + "-keep class test.pkg.KeepTest {\n"
                        + "    java.lang.Object myField\n"
                        + "}\n"
                        + "\n"
                        + "-keep class test.pkg.KeepTest {\n"
                        + "    void foo()\n"
                        + "}\n"
                        + "\n"
                        + "-keep class test.pkg.KeepTest.MyClass\n"
                        + "\n"
                        + "-keep enum test.pkg.KeepTest.MyEnum\n"
                        + "\n"
                        + "-keep interface test.pkg.KeepTest.MyInterface\n"
                        + "\n"
                        + "-keep interface test.pkg.KeepTest.MyInterface2 {\n"
                        + "    void paint2()\n"
                        + "}\n"
                        + "\n",
                Files.toString(output, Charsets.UTF_8));
        deleteFile(project);
    }

    public void testIncludeClassRetention() throws Exception {
        if (!isValidEcj()) {
            return;
        }

        File androidJar = findAndroidJar(false);
        if (androidJar == null) {
            System.err.println("Skipping test " + this.getClass() + "#" + getName()
                    + ": No android.jar found");
            return;
        }

        File project = createProject(
                mIntDefTest,
                mPermissionsTest,
                mManifest,
                mKeepAnnotation,
                mIntDefAnnotation,
                mIntRangeAnnotation,
                mPermissionAnnotation);

        File output = File.createTempFile("annotations", ".zip");
        File proguard = File.createTempFile("proguard", ".cfg");
        output.deleteOnExit();
        proguard.deleteOnExit();

        List<String> list = java.util.Arrays.asList(
                "--sources",
                new File(project, "src").getPath(),
                "--classpath",
                androidJar.getPath(),

                "--quiet",
                "--language-level",
                "1.6",
                "--output",
                output.getPath(),
                "--proguard",
                proguard.getPath()
        );
        String[] args = list.toArray(new String[list.size()]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check proguard rules
        assertEquals(""
                        + "-keep class test.pkg.IntDefTest {\n"
                        + "    void testIntDef(int)\n"
                        + "}\n"
                        + "\n",
                Files.toString(proguard, Charsets.UTF_8));

        // Check extracted annotations
        checkPackageXml("test.pkg", output, ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + "  <item name=\"test.pkg.IntDefTest void setFlags(java.lang.Object, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"test.pkg.IntDefTest void setStyle(int, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                + "    </annotation>\n"
                + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                + "      <val name=\"from\" val=\"20\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"test.pkg.PermissionsTest CONTENT_URI\">\n"
                + "    <annotation name=\"android.support.annotation.RequiresPermission.Read\">\n"
                + "      <val name=\"value\" val=\"&quot;android.permission.MY_READ_PERMISSION_STRING&quot;\" />\n"
                + "    </annotation>\n"
                + "    <annotation name=\"android.support.annotation.RequiresPermission.Write\">\n"
                + "      <val name=\"value\" val=\"&quot;android.permission.MY_WRITE_PERMISSION_STRING&quot;\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"test.pkg.PermissionsTest void myMethod()\">\n"
                + "    <annotation name=\"android.support.annotation.RequiresPermission\">\n"
                + "      <val name=\"value\" val=\"&quot;android.permission.MY_PERMISSION_STRING&quot;\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "</root>\n"
                + "\n");

        deleteFile(project);
    }

    public void testSkipClassRetention() throws Exception {
        if (!isValidEcj()) {
            return;
        }

        File androidJar = findAndroidJar(false);
        if (androidJar == null) {
            System.err.println("Skipping test " + this.getClass() + "#" + getName()
                    + ": No android.jar found");
            return;
        }

        File project = createProject(
                mIntDefTest,
                mPermissionsTest,
                mManifest,
                mKeepAnnotation,
                mIntDefAnnotation,
                mIntRangeAnnotation,
                mPermissionAnnotation);

        File output = File.createTempFile("annotations", ".zip");
        File proguard = File.createTempFile("proguard", ".cfg");
        output.deleteOnExit();
        proguard.deleteOnExit();

        List<String> list = java.util.Arrays.asList(
                "--sources",
                new File(project, "src").getPath(),
                "--classpath",
                androidJar.getPath(),

                "--quiet",
                "--skip-class-retention",
                "--language-level",
                "1.6",
                "--output",
                output.getPath(),
                "--proguard",
                proguard.getPath()
        );
        String[] args = list.toArray(new String[list.size()]);
        assertNotNull(args);

        new ExtractAnnotationsDriver().run(args);

        // Check external annotations
        checkPackageXml("test.pkg", output, ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + "  <item name=\"test.pkg.IntDefTest void setFlags(java.lang.Object, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"test.pkg.IntDefTest void setStyle(int, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{test.pkg.IntDefTest.STYLE_NORMAL, test.pkg.IntDefTest.STYLE_NO_TITLE, test.pkg.IntDefTest.STYLE_NO_FRAME, test.pkg.IntDefTest.STYLE_NO_INPUT}\" />\n"
                + "    </annotation>\n"
                + "    <annotation name=\"android.support.annotation.IntRange\">\n"
                + "      <val name=\"from\" val=\"20\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "</root>\n"
                + "\n");


        deleteFile(project);
    }

    @SuppressWarnings("SpellCheckingInspection")
    @NonNull
    private TestFile mksrc(@NonNull String to, @NonNull String source) {
        return new TestFile().to(to).withSource(source);
    }

    private File createProject(TestFile... files) throws IOException {
        File dir = Files.createTempDir();

        for (TestFile fp : files) {
            File file = fp.createFile(dir);
            assertNotNull(file);
        }

        return dir;
    }

    private final TestFile mKeepAnnotation = mksrc("src/android/support/annotation/Keep.java", ""
            + "package android.support.annotation;\n"
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.Target;\n"
            + "import static java.lang.annotation.ElementType.*;\n"
            + "import static java.lang.annotation.RetentionPolicy.*;\n"
            + "@Retention(CLASS)\n"
            + "@Target({PACKAGE,TYPE,ANNOTATION_TYPE,CONSTRUCTOR,METHOD,FIELD})\n"
            + "public @interface Keep {\n"
            + "}\n");

    private final TestFile mIntDefAnnotation = mksrc("src/android/support/annotation/IntDef.java", ""
            + "package android.support.annotation;\n"
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "import java.lang.annotation.Target;\n"
            + "import static java.lang.annotation.ElementType.*;\n"
            + "import static java.lang.annotation.RetentionPolicy.SOURCE;\n"
            + "@Retention(SOURCE)\n"
            + "@Target({ANNOTATION_TYPE})\n"
            + "public @interface IntDef {\n"
            + "    long[] value() default {};\n"
            + "    boolean flag() default false;\n"
            + "}\n");

    private final TestFile mIntRangeAnnotation = mksrc("src/android/support/annotation/IntRange.java", ""
            + "package android.support.annotation;\n"
            + "\n"
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.Target;\n"
            + "\n"
            + "import static java.lang.annotation.ElementType.*;\n"
            + "import static java.lang.annotation.RetentionPolicy.CLASS;\n"
            + "\n"
            + "@Retention(CLASS)\n"
            + "@Target({CONSTRUCTOR,METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})\n"
            + "public @interface IntRange {\n"
            + "    long from() default Long.MIN_VALUE;\n"
            + "    long to() default Long.MAX_VALUE;\n"
            + "}\n");

    private final TestFile mPermissionAnnotation = mksrc(
            "src/android/support/annotation/RequiresPermission.java", ""
            + "package android.support.annotation;\n"
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "import java.lang.annotation.Target;\n"
            + "import static java.lang.annotation.ElementType.*;\n"
            + "import static java.lang.annotation.RetentionPolicy.*;\n"
            + "@Retention(CLASS)\n"
            + "@Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD})\n"
            + "public @interface RequiresPermission {\n"
            + "    String value() default \"\";\n"
            + "    String[] allOf() default {};\n"
            + "    String[] anyOf() default {};\n"
            + "    boolean conditional() default false;\n"
            + "    @Target(FIELD)\n"
            + "    @interface Read {\n"
            + "        RequiresPermission value();\n"
            + "    }\n"
            + "    @Target(FIELD)\n"
            + "    @interface Write {\n"
            + "        RequiresPermission value();\n"
            + "    }\n"
            + "}");

    private final TestFile mIntDefTest = mksrc("src/test/pkg/IntDefTest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.content.Context;\n"
            + "import android.support.annotation.IntDef;\n"
            + "import android.support.annotation.IntRange;\n"
            + "import android.support.annotation.Keep;\n"
            + "import android.view.View;\n"
            + "\n"
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "\n"
            + "@SuppressWarnings(\"UnusedDeclaration\")\n"
            + "public class IntDefTest {\n"
            + "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n"
            + "    @IntRange(from = 20)\n"
            + "    @Retention(RetentionPolicy.SOURCE)\n"
            + "    private @interface DialogStyle {}\n"
            + "\n"
            + "    public static final int STYLE_NORMAL = 0;\n"
            + "    public static final int STYLE_NO_TITLE = 1;\n"
            + "    public static final int STYLE_NO_FRAME = 2;\n"
            + "    public static final int STYLE_NO_INPUT = 3;\n"
            + "    public static final int UNRELATED = 3;\n"
            + "\n"
            + "    public void setStyle(@DialogStyle int style, int theme) {\n"
            + "    }\n"
            + "\n"
            + "    @Keep"
            + "    public void testIntDef(int arg) {\n"
            + "    }\n"
            + "    @IntDef(value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT}, flag=true)\n"
            + "    @Retention(RetentionPolicy.SOURCE)\n"
            + "    private @interface DialogFlags {}\n"
            + "\n"
            + "    public void setFlags(Object first, @DialogFlags int flags) {\n"
            + "    }\n"
            + "\n"
            + "    public static final String TYPE_1 = \"type1\";\n"
            + "    public static final String TYPE_2 = \"type2\";\n"
            + "    public static final String UNRELATED_TYPE = \"other\";\n"
            + "}");

    private final TestFile mPermissionsTest = mksrc("src/test/pkg/PermissionsTest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "import android.support.annotation.RequiresPermission;\n"
            + "\n"
            + "public class PermissionsTest {\n"
            + "    @RequiresPermission(Manifest.permission.MY_PERMISSION)\n"
            + "    public void myMethod() {\n"
            + "    }\n"
            + "\n"
            + "    @RequiresPermission.Read(@RequiresPermission(Manifest.permission.MY_READ_PERMISSION))\n"
            + "    @RequiresPermission.Write(@RequiresPermission(Manifest.permission.MY_WRITE_PERMISSION))\n"
            + "    public static final String CONTENT_URI = \"\";\n"
            + "}\n");

    private final TestFile mManifest = mksrc("src/test/pkg/Manifest.java", ""
            + "package test.pkg;\n"
            + "\n"
            + "public class Manifest {\n"
            + "    public static final class permission {\n"
            + "        public static final String MY_PERMISSION = \"android.permission.MY_PERMISSION_STRING\";\n"
            + "        public static final String MY_READ_PERMISSION = \"android.permission.MY_READ_PERMISSION_STRING\";\n"
            + "        public static final String MY_WRITE_PERMISSION = \"android.permission.MY_WRITE_PERMISSION_STRING\";\n"
            + "    }\n"
            + "}\n");

    private final TestFile mKeepTest = mksrc("src/test/pkg/KeepTest.java", ""
            + "package test.pkg;\n"
            + "import android.support.annotation.Keep;\n"
            + "\n"
            + "public class KeepTest {\n"
            + "    @Keep\n"
            + "    public void foo() {\n"
            + "    }\n"
            + "\n"
            + "    @Keep\n"
            + "    public static class MyClass {\n"
            + "        public void paint() {\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    @Keep\n"
            + "    public static interface MyInterface {\n"
            + "        public void paint();\n"
            + "    }\n"
            + "\n"
            + "    public static interface MyInterface2 {\n"
            + "        @Keep\n"
            + "        public void paint2();\n"
            + "    }\n"
            + "\n"
            + "    @Keep\n"
            + "    public static enum MyEnum {\n"
            + "        TYPE1, TYPE2;\n"
            + "    }\n"
            + "\n"
            + "    @Keep\n"
            + "    public static @interface MyAnnotation {\n"
            + "    }\n"
            + "\n"
            + "    @Keep\n"
            + "    public Object myField = null;"
            + "}\n");

    private static void checkPackageXml(String pkg, File output, String expected)
            throws IOException {
        assertNotNull(output);
        assertTrue(output.exists());
        URL url = new URL("jar:" + fileToUrlString(output) + "!/" + pkg.replace('.','/') +
                "/annotations.xml");
        InputStream stream = url.openStream();
        try {
            byte[] bytes = ByteStreams.toByteArray(stream);
            assertNotNull(bytes);
            String xml = new String(bytes, Charsets.UTF_8);
            assertEquals(expected, xml);
        } finally {
            Closeables.closeQuietly(stream);
        }
    }

    @Nullable
    private static File findAndroidJar(boolean requireExists) {
        String androidHomePath = System.getenv("ANDROID_HOME");
        assertNotNull("Must set $ANDROID_HOME to run this test", androidHomePath);
        File androidHome = new File(androidHomePath);
        if (!androidHome.exists()) {
            if (!requireExists) {
                return null;
            }
            fail(androidHomePath + " does not exist");
        }
        File androidJar = new File(androidHome, "platforms/android-22/android.jar");
        assertTrue(
                androidJar + " does not exist: make sure you have Lollipop installed in your SDK",
                androidJar.exists());
        return androidJar;
    }

    public void testGetRaw() throws Exception {
        assertEquals("", ApiDatabase.getRawClass(""));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo"));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo<T>"));
        assertEquals("Foo", ApiDatabase.getRawMethod("Foo<T>"));
        assertEquals("Foo", ApiDatabase.getRawClass("Foo<A,B>"));
        assertEquals("Foo", ApiDatabase.getRawParameterList("Foo<? extends java.util.List>"));
        assertEquals("Object,java.util.List,List,int[],Object[]",
                ApiDatabase.getRawParameterList("Object<? extends java.util.List>,java.util.List<String>,"
                        + "List<? super Number>,int[],Object..."));
    }
}