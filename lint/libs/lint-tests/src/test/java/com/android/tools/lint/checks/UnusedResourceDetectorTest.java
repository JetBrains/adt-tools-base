/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.tools.lint.detector.api.TextFormat.TEXT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class UnusedResourceDetectorTest extends AbstractCheckTest {
    private boolean mEnableIds = false;

    @Override
    protected Detector getDetector() {
        return new UnusedResourceDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    @Override
    protected boolean isEnabled(Issue issue) {
        //noinspection SimplifiableIfStatement
        if (issue == UnusedResourceDetector.ISSUE_IDS) {
            return mEnableIds;
        } else {
            return true;
        }
    }

    public void testUnused() throws Exception {
        mEnableIds = false;
        assertEquals(""
                + "res/layout/accessibility.xml:2: Warning: The resource R.layout.accessibility appears to be unused [UnusedResources]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                + "^\n"
                + "res/layout/main.xml:2: Warning: The resource R.layout.main appears to be unused [UnusedResources]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "res/layout/other.xml:2: Warning: The resource R.layout.other appears to be unused [UnusedResources]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "res/values/strings2.xml:3: Warning: The resource R.string.hello appears to be unused [UnusedResources]\n"
                + "    <string name=\"hello\">Hello</string>\n"
                + "            ~~~~~~~~~~~~\n"
                + "0 errors, 4 warnings\n",

            lintProject(
                "res/values/strings2.xml",
                "res/layout/layout1.xml=>res/layout/main.xml",
                "res/layout/layout1.xml=>res/layout/other.xml",

                // Rename .txt files to .java
                "src/my/pkg/Test.java.txt=>src/my/pkg/Test.java",
                "gen/my/pkg/R.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml",
                "res/layout/accessibility.xml"));
    }

    public void testUnusedIds() throws Exception {
        mEnableIds = true;

        assertEquals(""
                + "res/layout/accessibility.xml:2: Warning: The resource R.layout.accessibility appears to be unused [UnusedResources]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                + "^\n"
                + "res/layout/accessibility.xml:2: Warning: The resource R.id.newlinear appears to be unused [UnusedIds]\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                + "                                                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:3: Warning: The resource R.id.button1 appears to be unused [UnusedIds]\n"
                + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:4: Warning: The resource R.id.android_logo appears to be unused [UnusedIds]\n"
                + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                + "               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/layout/accessibility.xml:5: Warning: The resource R.id.android_logo2 appears to be unused [UnusedIds]\n"
                + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                + "                                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 5 warnings\n",

            lintProject(
                "src/my/pkg/Test.java.txt=>src/my/pkg/Test.java",
                "gen/my/pkg/R.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml",
                "res/layout/accessibility.xml"));
    }

    public void testImplicitFragmentUsage() throws Exception {
        mEnableIds = true;
        // Regression test for https://code.google.com/p/android/issues/detail?id=209393
        // Ensure fragment id's aren't deleted.
        assertEquals("No warnings.",

                lintProject(
                        xml("res/layout/has_fragment.xml", ""
                                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                + "<fragment\n"
                                + "    android:id=\"@+id/viewer\"\n"
                                + "    android:name=\"package.name.MyFragment\"\n"
                                + "    android:layout_width=\"match_parent\"\n"
                                + "    android:layout_height=\"match_parent\"/>\n"
                                + "</LinearLayout>\n"),
                        java("src/test/pkg/Test.java", ""
                                + "package test.pkg;\n"
                                + "public class Test {\n"
                                + "    public void test() {"
                                + "        int used = R.layout.has_fragment;\n"
                                + "    }"
                                + "}")
                ));
    }

    public void testArrayReference() throws Exception {
        mEnableIds = false;
        assertEquals(""
                // The string is unused, but only because the array referencing it is unused too.
                + "res/values/arrayusage.xml:2: Warning: The resource R.string.my_item appears to be unused [UnusedResources]\n"
                + "<string name=\"my_item\">An Item</string>\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "res/values/arrayusage.xml:3: Warning: The resource R.array.my_array appears to be unused [UnusedResources]\n"
                + "<string-array name=\"my_array\">\n"
                + "              ~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

            lintProject(
                xml("res/values/arrayusage.xml", ""
                        + "<resources>\n"
                        + "<string name=\"my_item\">An Item</string>\n"
                        + "<string-array name=\"my_array\">\n"
                        + "   <item>@string/my_item</item>\n"
                        + "</string-array>\n"
                        + "</resources>\n")
                     ));
    }

    public void testArrayReferenceIncluded() throws Exception {
        mEnableIds = false;
        assertEquals("No warnings.",

                lintProject(
                        xml("res/values/arrayusage.xml", ""
                                + "<resources xmlns:tools=\"http://schemas.android.com/tools\""
                                + "   tools:keep=\"@array/my_array\">\n"
                                + "<string name=\"my_item\">An Item</string>\n"
                                + "<string-array name=\"my_array\">\n"
                                + "   <item>@string/my_item</item>\n"
                                + "</string-array>\n"
                                + "</resources>\n")
                ));
    }

    public void testAttrs() throws Exception {
        assertEquals(""
                + "res/layout/customattrlayout.xml:2: Warning: The resource R.layout.customattrlayout appears to be unused [UnusedResources]\n"
                + "<foo.bar.ContentFrame\n"
                + "^\n"
                + "0 errors, 1 warnings\n",

            lintProject(
                "res/values/customattr.xml",
                "res/layout/customattrlayout.xml",
                "unusedR.java.txt=>gen/my/pkg/R.java",
                "AndroidManifest.xml"));
    }

    public void testMultiProjectIgnoreLibraries() throws Exception {
        assertEquals(
           "No warnings.",

            lintProject(
                // Master project
                "multiproject/main-manifest.xml=>AndroidManifest.xml",
                "multiproject/main.properties=>project.properties",
                "multiproject/MainCode.java.txt=>src/foo/main/MainCode.java",

                // Library project
                "multiproject/library-manifest.xml=>../LibraryProject/AndroidManifest.xml",
                "multiproject/library.properties=>../LibraryProject/project.properties",
                "multiproject/LibraryCode.java.txt=>../LibraryProject/src/foo/library/LibraryCode.java",
                "multiproject/strings.xml=>../LibraryProject/res/values/strings.xml"
            ));
    }

    public void testMultiProject() throws Exception {
        File master = getProjectDir("MasterProject",
                // Master project
                "multiproject/main-manifest.xml=>AndroidManifest.xml",
                "multiproject/main.properties=>project.properties",
                "multiproject/MainCode.java.txt=>src/foo/main/MainCode.java"
        );
        File library = getProjectDir("LibraryProject",
                // Library project
                "multiproject/library-manifest.xml=>AndroidManifest.xml",
                "multiproject/library.properties=>project.properties",
                "multiproject/LibraryCode.java.txt=>src/foo/library/LibraryCode.java",
                "multiproject/strings.xml=>res/values/strings.xml"
        );
        assertEquals(
           // string1 is defined and used in the library project
           // string2 is defined in the library project and used in the master project
           // string3 is defined in the library project and not used anywhere
           "LibraryProject/res/values/strings.xml:7: Warning: The resource R.string.string3 appears to be unused [UnusedResources]\n" +
           "    <string name=\"string3\">String 3</string>\n" +
           "            ~~~~~~~~~~~~~~\n" +
           "0 errors, 1 warnings\n",

           checkLint(Arrays.asList(master, library)).replace("/TESTROOT/", ""));
    }

    public void testFqcnReference() throws Exception {
        assertEquals(
           "No warnings.",

            lintProject(
                "res/layout/layout1.xml=>res/layout/main.xml",
                "src/test/pkg/UnusedReference.java.txt=>src/test/pkg/UnusedReference.java",
                "AndroidManifest.xml"));
    }

    /* Not sure about this -- why would we ignore drawable XML?
    public void testIgnoreXmlDrawable() throws Exception {
        assertEquals(
           "No warnings.",

            lintProject(
                    "res/drawable/ic_menu_help.xml",
                    "gen/my/pkg/R2.java.txt=>gen/my/pkg/R.java"
            ));
    }
    */

    public void testPlurals() throws Exception {
        //noinspection ClassNameDiffersFromFileName
        assertEquals("No warnings.",
            lintProject(
                copy("res/values/strings4.xml"),
                copy("res/values/plurals.xml"),
                copy("AndroidManifest.xml"),
                java("src/test/pkg/Test.java", ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    public void test() {"
                        + "        int used = R.plurals.my_plural;\n"
                        + "    }"
                        + "}")
            ));
    }

    public void testNoMerging() throws Exception {
        // http://code.google.com/p/android/issues/detail?id=36952

        File master = getProjectDir("MasterProject",
                // Master project
                "multiproject/main-manifest.xml=>AndroidManifest.xml",
                "multiproject/main.properties=>project.properties",
                "multiproject/MainCode.java.txt=>src/foo/main/MainCode.java"
        );
        File library = getProjectDir("LibraryProject",
                // Library project
                "multiproject/library-manifest.xml=>AndroidManifest.xml",
                "multiproject/library.properties=>project.properties",
                "multiproject/LibraryCode.java.txt=>src/foo/library/LibraryCode.java",
                "multiproject/strings.xml=>res/values/strings.xml"
        );
        assertEquals(
           // The strings are all referenced in the library project's manifest file
           // which in this project is merged in
           "LibraryProject/res/values/strings.xml:7: Warning: The resource R.string.string3 appears to be unused [UnusedResources]\n" +
           "    <string name=\"string3\">String 3</string>\n" +
           "            ~~~~~~~~~~~~~~\n" +
           "0 errors, 1 warnings\n",

           checkLint(Arrays.asList(master, library)).replace("/TESTROOT/", ""));
    }

    public void testLibraryMerging() throws Exception {
        // http://code.google.com/p/android/issues/detail?id=36952
        File master = getProjectDir("MasterProject",
                // Master project
                "multiproject/main-manifest.xml=>AndroidManifest.xml",
                "multiproject/main-merge.properties=>project.properties",
                "multiproject/MainCode.java.txt=>src/foo/main/MainCode.java"
        );
        File library = getProjectDir("LibraryProject",
                // Library project
                "multiproject/library-manifest.xml=>AndroidManifest.xml",
                "multiproject/library.properties=>project.properties",
                "multiproject/LibraryCode.java.txt=>src/foo/library/LibraryCode.java",
                "multiproject/strings.xml=>res/values/strings.xml"
        );
        assertEquals(
           // The strings are all referenced in the library project's manifest file
           // which in this project is merged in
           "No warnings.",

           checkLint(Arrays.asList(master, library)));
    }

    public void testCornerCase() throws Exception {
        // See http://code.google.com/p/projectlombok/issues/detail?id=415
        mEnableIds = true;
        assertEquals(
            "No warnings.",

             lintProject(
                 "src/test/pkg/Foo.java.txt=>src/test/pkg/Foo.java",
                 "AndroidManifest.xml"));
    }

    public void testAnalytics() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=42565
        mEnableIds = false;
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/analytics.xml"
        ));
    }

    public void testIntegers() throws Exception {
        // See https://code.google.com/p/android/issues/detail?id=53995
        mEnableIds = true;
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/integers.xml",
                        "res/anim/slide_in_out.xml"
                ));
    }

    public void testIntegerArrays() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=59761
        mEnableIds = false;
        assertEquals(
                "No warnings.",
                lintProject("res/values/integer_arrays.xml=>res/values/integer_arrays.xml"));
    }

    public void testUnitTestReferences() throws Exception {
        // Make sure that we pick up references in unit tests as well
        // Regression test for
        // https://code.google.com/p/android/issues/detail?id=79066
        mEnableIds = false;
        //noinspection ClassNameDiffersFromFileName
        assertEquals("No warnings.",

                lintProject(
                        copy("res/values/strings2.xml"),
                        copy("res/layout/layout1.xml", "res/layout/main.xml"),
                        copy("res/layout/layout1.xml", "res/layout/other.xml"),

                        copy("src/my/pkg/Test.java.txt", "src/my/pkg/Test.java"),
                        copy("gen/my/pkg/R.java.txt", "gen/my/pkg/R.java"),
                        copy("AndroidManifest.xml"),
                        copy("res/layout/accessibility.xml"),

                        // Add unit test source which references resources which would otherwise
                        // be marked as unused
                        java("test/my/pkg/MyTest.java", ""
                                + "package my.pkg;\n"
                                + "class MyTest {\n"
                                + "    public void test() {\n"
                                + "        System.out.println(R.layout.accessibility);\n"
                                + "        System.out.println(R.layout.main);\n"
                                + "        System.out.println(R.layout.other);\n"
                                + "        System.out.println(R.string.hello);\n"
                                + "    }\n"
                                + "}\n")
                        ));
    }

    public void testDataBinding() throws Exception {
        // Make sure that resources referenced only via a data binding expression
        // are not counted as unused.
        // Regression test for https://code.google.com/p/android/issues/detail?id=183934
        mEnableIds = false;
        assertEquals("No warnings.",

                lintProject(
                        xml("res/values/resources.xml", ""
                                + "<resources>\n"
                                + "    <item type='dimen' name='largePadding'>20dp</item>\n"
                                + "    <item type='dimen' name='smallPadding'>15dp</item>\n"
                                + "    <item type='string' name='nameFormat'>%1$s %2$s</item>\n"
                                + "</resources>"),

                        // Add unit test source which references resources which would otherwise
                        // be marked as unused
                        xml("res/layout/db.xml", ""
                                + "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                + "    xmlns:tools=\"http://schemas.android.com/tools\" "
                                + "    tools:keep=\"@layout/db\">\n"
                                + "   <data>\n"
                                + "       <variable name=\"user\" type=\"com.example.User\"/>\n"
                                + "   </data>\n"
                                + "   <LinearLayout\n"
                                + "       android:orientation=\"vertical\"\n"
                                + "       android:layout_width=\"match_parent\"\n"
                                + "       android:layout_height=\"match_parent\"\n"
                                // Data binding expressions
                                + "       android:padding=\"@{large? @dimen/largePadding : @dimen/smallPadding}\"\n"
                                + "       android:text=\"@{@string/nameFormat(firstName, lastName)}\" />\n"
                                + "</layout>")
                ));
    }

    public void testDataBindingIds() throws Exception {
        // Make sure id's in data binding layouts aren't considered unused
        // (since the compiler will generate accessors for these that
        // may not be visible when running lint on edited sources)
        // Regression test for https://code.google.com/p/android/issues/detail?id=189065
        mEnableIds = true;
        assertEquals("No warnings.",

                lintProject(
                        xml("res/layout/db.xml", ""
                                + "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                + "    xmlns:tools=\"http://schemas.android.com/tools\" "
                                + "    tools:keep=\"@layout/db\">\n"
                                + "   <data>\n"
                                + "       <variable name=\"user\" type=\"com.example.User\"/>\n"
                                + "   </data>\n"
                                + "   <LinearLayout\n"
                                + "       android:orientation=\"vertical\"\n"
                                + "       android:id=\"@+id/my_id\"\n"
                                + "       android:layout_width=\"match_parent\"\n"
                                + "       android:layout_height=\"match_parent\" />\n"
                                + "</layout>")
                ));
    }

    public void testPublic() throws Exception {
        // Resources marked as public should not be listed as potentially unused
        mEnableIds = false;
        assertEquals(""
                + "res/values/resources.xml:4: Warning: The resource R.string.nameFormat appears to be unused [UnusedResources]\n"
                + "    <item type='string' name='nameFormat'>%1$s %2$s</item>\n"
                + "                        ~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        xml("res/values/resources.xml", ""
                                + "<resources>\n"
                                + "    <item type='dimen' name='largePadding'>20dp</item>\n"
                                + "    <item type='dimen' name='smallPadding'>15dp</item>\n"
                                + "    <item type='string' name='nameFormat'>%1$s %2$s</item>\n"
                                + "    <public type='dimen' name='largePadding' />"
                                + "    <public type='dimen' name='smallPadding' />"
                                + "</resources>")
                ));
    }

    public void testDynamicResources() throws Exception {
        assertEquals(""
                        + "UnusedResourceDetectorTest_testDynamicResources: Warning: The resource R.string.cat appears to be unused [UnusedResources]\n"
                        + "UnusedResourceDetectorTest_testDynamicResources: Warning: The resource R.string.dog appears to be unused [UnusedResources]\n"
                        + "0 errors, 2 warnings\n",

                lintProject(
                        "res/layout/layout1.xml=>res/layout/main.xml",
                        "src/test/pkg/UnusedReferenceDynamic.java.txt=>src/test/pkg/UnusedReferenceDynamic.java",
                        "AndroidManifest.xml"));
    }

    public void testStaticImport() throws Exception {
        // Regression test for https://code.google.com/p/android/issues/detail?id=40293
        // 40293: Lint reports resource as unused when referenced via "import static"
        mEnableIds = false;
        assertEquals(""
                + "No warnings.",

                lintProject(
                        xml("res/values/resources.xml", ""
                                + "<resources>\n"
                                + "    <item type='dimen' name='largePadding'>20dp</item>\n"
                                + "    <item type='dimen' name='smallPadding'>15dp</item>\n"
                                + "    <item type='string' name='nameFormat'>%1$s %2$s</item>\n"
                                + "</resources>"),

                        // Add unit test source which references resources which would otherwise
                        // be marked as unused
                        java("src/test/pkg/TestCode.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import static test.pkg.R.dimen.*;\n"
                                + "import static test.pkg.R.string.nameFormat;\n"
                                + "import test.pkg.R.dimen;\n"
                                + "\n"
                                + "public class TestCode {\n"
                                + "    public void test() {\n"
                                + "        int x = dimen.smallPadding; // Qualified import\n"
                                + "        int y = largePadding; // Static wildcard import\n"
                                + "        int z = nameFormat; // Static explicit import\n"
                                + "    }\n"
                                + "}\n"),
                        java("src/test/pkg/R.java", ""
                                + "package test.pkg;\n"
                                + "public class R {\n"
                                + "    public static class dimen {\n"
                                + "        public static final int largePadding = 1;\n"
                                + "        public static final int smallPadding = 2;\n"
                                + "    }\n"
                                + "    public static class string {\n"
                                + "        public static final int nameFormat = 3;\n"
                                + "    }\n"
                                + "}")
                ));
    }

    public void testStyles() throws Exception {
        mEnableIds = false;
        assertEquals(""
                + "res/values/styles.xml:5: Warning: The resource R.style.UnusedStyle appears to be unused [UnusedResources]\n"
                + "    <style name=\"UnusedStyle\"/>\n"
                + "           ~~~~~~~~~~~~~~~~~~\n"
                + "res/values/styles.xml:6: Warning: The resource R.style.UnusedStyle_Sub appears to be unused [UnusedResources]\n"
                + "    <style name=\"UnusedStyle.Sub\"/>\n"
                + "           ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values/styles.xml:7: Warning: The resource R.style.UnusedStyle_Something_Sub appears to be unused [UnusedResources]\n"
                + "    <style name=\"UnusedStyle.Something.Sub\" parent=\"UnusedStyle\"/>\n"
                + "           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 3 warnings\n",

                lintProject(
                        xml("res/values/styles.xml", ""
                                + "<resources>\n"
                                + "    <style name=\"UsedStyle\" parent=\"android:Theme\"/>\n"
                                + "    <style name=\"UsedStyle.Sub\"/>\n"
                                + "    <style name=\"UsedStyle.Something.Sub\" parent=\"UsedStyle\"/>\n"

                                + "    <style name=\"UnusedStyle\"/>\n"
                                + "    <style name=\"UnusedStyle.Sub\"/>\n"
                                + "    <style name=\"UnusedStyle.Something.Sub\" parent=\"UnusedStyle\"/>\n"

                                + "    <style name=\"ImplicitUsed\" parent=\"android:Widget.ActionBar\"/>\n"
                                + "</resources>")
                ));
    }


    public void testThemeFromLayout() throws Exception {
        mEnableIds = false;
        assertEquals("No warnings.",

                lintProject(
                        xml("res/values/styles.xml", ""
                                + "<resources>\n"
                                + "    <style name=\"InlineActionView\" />\n"
                                + "    <style name=\"InlineActionView.Like\">\n"
                                + "    </style>\n"
                                + "</resources>\n"),
                        xml("res/layout/main.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    android:orientation=\"vertical\" android:layout_width=\"match_parent\"\n"
                                + "    android:layout_height=\"match_parent\">\n"
                                + "\n"
                                + "    <Button\n"
                                + "        android:layout_width=\"wrap_content\"\n"
                                + "        android:layout_height=\"wrap_content\"\n"
                                + "        style=\"@style/InlineActionView.Like\"\n"
                                + "        android:layout_gravity=\"center_horizontal\" />\n"
                                + "</LinearLayout>"),
                        java("test/my/pkg/MyTest.java", ""
                                + "package my.pkg;\n"
                                + "class MyTest {\n"
                                + "    public void test() {\n"
                                + "        System.out.println(R.layout.main);\n"
                                + "    }\n"
                                + "}\n")
                ));
    }

    public void testKeepAndDiscard() throws Exception {
        mEnableIds = false;
        assertEquals("No warnings.",

                lintProject(
                        // By name
                        xml("res/raw/keep.xml", ""
                                + "<foo/>"),

                        // By content
                        xml("res/raw/used.xml", ""
                                + "<resources\n"
                                + "        xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                + "        tools:shrinkMode=\"strict\"\n"
                                + "        tools:discard=\"@raw/unused\"\n"
                                + "        tools:keep=\"@raw/used\" />\n")

                ));
    }

    @Override
    protected TestLintClient createClient() {
        if (!getName().startsWith("testDynamicResources")) {
            return super.createClient();
        }

        // Set up a mock project model for the resource configuration test(s)
        // where we provide a subset of densities to be included

        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {
                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Nullable
                    @Override
                    public AndroidProject getGradleProjectModel() {
                        /*
                        Simulate dynamic resources in this setup:
                            defaultConfig {
                                ...
                                resValue "string", "cat", "Some Data"
                            }
                            buildTypes {
                                debug {
                                    ...
                                    resValue "string", "foo", "Some Data"
                                }
                                release {
                                    ...
                                    resValue "string", "xyz", "Some Data"
                                    resValue "string", "dog", "Some Data"
                                }
                            }
                         */
                        ClassField foo = mock(ClassField.class);
                        when(foo.getName()).thenReturn("foo");
                        when(foo.getType()).thenReturn("string");
                        ClassField xyz = mock(ClassField.class);
                        when(xyz.getName()).thenReturn("xyz");
                        when(xyz.getType()).thenReturn("string");
                        ClassField cat = mock(ClassField.class);
                        when(cat.getName()).thenReturn("cat");
                        when(cat.getType()).thenReturn("string");
                        ClassField dog = mock(ClassField.class);
                        when(dog.getName()).thenReturn("dog");
                        when(dog.getType()).thenReturn("string");

                        Map<String, ClassField> debugResValues = ImmutableMap.of("foo", foo);
                        BuildType type1 = mock(BuildType.class);
                        when(type1.getName()).thenReturn("debug");
                        when(type1.getResValues()).thenReturn(debugResValues);
                        Map<String, ClassField> releaseResValues =
                                ImmutableMap.of("xyz", xyz, "dog", dog);
                        BuildType type2 = mock(BuildType.class);
                        when(type2.getName()).thenReturn("release");
                        when(type2.getResValues()).thenReturn(releaseResValues);

                        BuildTypeContainer container1 = mock(BuildTypeContainer.class);
                        when(container1.getBuildType()).thenReturn(type1);
                        BuildTypeContainer container2 = mock(BuildTypeContainer.class);
                        when(container2.getBuildType()).thenReturn(type2);

                        SourceProvider debugProvider = mock(SourceProvider.class);
                        when(debugProvider.getResDirectories()).thenReturn(Collections.<File>emptyList());
                        when(debugProvider.getJavaDirectories()).thenReturn(Collections.<File>emptyList());
                        SourceProvider releaseProvider = mock(SourceProvider.class);
                        when(releaseProvider.getResDirectories()).thenReturn(Collections.<File>emptyList());
                        when(releaseProvider.getJavaDirectories()).thenReturn(Collections.<File>emptyList());

                        when(container1.getSourceProvider()).thenReturn(debugProvider);
                        when(container2.getSourceProvider()).thenReturn(releaseProvider);

                        Map<String, ClassField> defaultResValues = ImmutableMap.of("cat", cat);
                        ProductFlavor defaultFlavor = mock(ProductFlavor.class);
                        when(defaultFlavor.getResValues()).thenReturn(defaultResValues);

                        ProductFlavorContainer defaultContainer =
                                mock(ProductFlavorContainer.class);
                        when(defaultContainer.getProductFlavor()).thenReturn(defaultFlavor);

                        AndroidProject project = mock(AndroidProject.class);
                        when(project.getDefaultConfig()).thenReturn(defaultContainer);
                        when(project.getBuildTypes())
                                .thenReturn(Arrays.asList(container1, container2));
                        return project;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        Variant variant = mock(Variant.class);
                        when(variant.getBuildType()).thenReturn("release");
                        return variant;
                    }
                };
            }
        };
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        assertNotNull(message, UnusedResourceDetector.getUnusedResource(message, TEXT));
    }
}
