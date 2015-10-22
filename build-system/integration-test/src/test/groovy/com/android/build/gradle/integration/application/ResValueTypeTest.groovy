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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Test resValue for string type is treated as String.
 */
@CompileStatic
class ResValueTypeTest {
    static AndroidTestApp app = HelloWorldApp.noBuildFile()
    static {
        app.removeFile(app.getFile("HelloWorldTest.java"))
        app.addFile(new TestSourceFile("src/androidTest/java/com/example/helloworld", "ResValueTest.java",
"""
package com.example.helloworld;

import android.test.AndroidTestCase;

public class ResValueTest extends AndroidTestCase {
    public void testResValue() {
        assertEquals("00", getContext().getString(R.string.resString));
    }
}
"""))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(app)
            .create()

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

    defaultConfig {
        resValue "array",             "resArray",            "foo"
        resValue "attr",              "resAttr",             "foo"
        resValue "bool",              "resBool",             "true"
        resValue "color",             "resColor",            "#ffffff"
        resValue "declare-styleable", "resDeclareStyleable", "foo"
        resValue "dimen",             "resDimen",            "42px"
        resValue "fraction",          "resFraction",         "42%"
        resValue "id",                "resId",               "42"
        resValue "integer",           "resInteger",          "42"
        resValue "plurals",           "resPlurals",          "s"
        resValue "string",            "resString",           "00"  // resString becomes "0" if it is incorrectly treated  as int.
        resValue "style",             "resStyle",            "foo"
    }
}
"""
    }

    @AfterClass
    static void cleanUp() {
        project = null
        app = null
    }

    @Test
    void "check <string> tag is used in generated.xml" () {
        project.execute("clean", "generateDebugResValue")
        File outputFile = project.file("build/generated/res/resValues/debug/values/generated.xml")
        assertTrue("Missing file: " + outputFile, outputFile.isFile())
        assertEquals(
"""<?xml version="1.0" encoding="utf-8"?>
<resources>

    <!-- Automatically generated file. DO NOT MODIFY -->

    <!-- Values from default config. -->
    <array name="resArray">foo</array>

    <attr name="resAttr">foo</attr>

    <bool name="resBool">true</bool>

    <color name="resColor">#ffffff</color>

    <declare-styleable name="resDeclareStyleable">foo</declare-styleable>

    <dimen name="resDimen">42px</dimen>

    <fraction name="resFraction">42%</fraction>

    <item name="resId" type="id">42</item>

    <integer name="resInteger">42</integer>

    <plurals name="resPlurals">s</plurals>

    <string name="resString" translatable="false">00</string>

    <style name="resStyle">foo</style>

</resources>""", FileUtils.loadFileWithUnixLineSeparators(outputFile))
    }

    @Test
    @Category(DeviceTests.class)
    void "check resValue is treated as string"() {
        project.execute("clean")
        project.executeConnectedCheck()
    }
}
