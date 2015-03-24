/*
 * Copyright (C) 2014 The Android Open Source Project
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



package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Integration test for extracting annotations.
 */
class ExtractAnnotationTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("extractAnnotations")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check extract annotation"() {
        File debugFileOutput = project.file("build/$AndroidProject.FD_INTERMEDIATES/annotations/debug")
        File file = new File(debugFileOutput, "annotations.zip")

        //noinspection SpellCheckingInspection
        String expectedContent = (""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest ExtractTest(int, java.lang.String) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getHiddenMethod()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getPrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getVisibility()\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "    <annotation name=\"android.support.annotation.ColorRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.Object getPackagePrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1, com.android.tests.extractannotations.ExtractTest.STRING_2, &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void checkForeignTypeDef(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 2\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.FLAG_VALUE_1, com.android.tests.extractannotations.Constants.FLAG_VALUE_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testNonMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_3}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                // This should be hidden when we start filtering out hidden classes on @hide!
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest.HiddenClass int getHiddenMember()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "</root>")


        assertThatZip(file).containsFileWithContent(
                "com/android/tests/extractannotations/annotations.xml", expectedContent)

        // check the resulting .aar file to ensure annotations.zip inclusion.
        assertThatZip(project.getAar("debug")).contains("annotations.zip")
    }
}
