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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Check resource removed implicitly by overriding resource from a library that includes an @+id
 * with one that does not causes the build to fail.
 */
@CompileStatic
class ResourceOverrideTest {

    public static final AndroidTestApp parentBuild = new EmptyAndroidTestApp();
    public static final AndroidTestApp testApp = new EmptyAndroidTestApp("com.example.app");
    public static final AndroidTestApp testLib = new EmptyAndroidTestApp("com.example.lib");
    static {
        parentBuild.addFile(new TestSourceFile("", "settings.gradle", "include ':app', ':lib'"))

        testApp.addFile(new TestSourceFile("src/main/res/layout", "activity_main.xml", """
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <TextView android:text="Hello world" />
</RelativeLayout>"""));

        testLib.addFile(new TestSourceFile("src/main/res/layout", "activity_main.xml", """
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <TextView android:text="Hello world" android:id="@+id/hello_world_text" />
</RelativeLayout>"""));
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
        .fromTestApp(parentBuild)
        .captureStdOut(true)
        .captureStdErr(true)
        .create()

    @BeforeClass
    public static void setUp() {
        FileUtils.mkdirs(project.file("lib"))
        GradleTestProject libProject = project.getSubproject("lib")
        testLib.write(libProject.file(""),"""
apply plugin: 'com.android.library'

android {
    compileSdkVersion 23
    buildToolsVersion rootProject.buildToolsVersion
}""")
        FileUtils.mkdirs(project.file("app"))
        GradleTestProject appProject = project.getSubproject("app")
        testApp.write(appProject.file(""),"""
apply plugin: 'com.android.application'

android {
    compileSdkVersion 23
    buildToolsVersion rootProject.buildToolsVersion
}

dependencies {
    compile project(':lib')
}""")

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void "check resource removed implicitly by overriding resource from a library that\
                  includes an @+id causes the build to fail"() {
        project.getStderr().reset();
        project.executeExpectingFailure(":app:assembleDebug")
        assertThat(project.getStderr().toString()).contains("Error: Library with package name " +
                "'com.example.lib' defines resource 'hello_world_text', but a resource override " +
                "in project with package name 'com.example.app' has removed it")
    }
}
