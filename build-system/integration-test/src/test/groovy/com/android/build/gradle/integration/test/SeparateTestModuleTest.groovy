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

package com.android.build.gradle.integration.test

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.TestedTargetVariant
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
/**
 * Test for setup with 2 modules: app and test-app
 * Checking the manifest merging for the test modules.
 */
@CompileStatic
class SeparateTestModuleTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .create()

    @Before
    public void setUp() {
        project.getSubproject("test").getBuildFile() << """
android {
    defaultConfig {
        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
    }
}
"""
    }

    @Test
    public void "check dependencies between tasks"() throws Exception {
        // Check :test:assembleDebug succeeds on its own, i.e. compiles the app module.
        project.execute("clean", ":test:assembleDebug", ":test:checkDependencies")
    }

    @Test
    public void "check instrumentation read from build file"() throws Exception {
        GradleTestProject testProject = project.getSubproject("test")
        addInstrumentationToManifest()
        project.execute("clean", ":test:assembleDebug")

        assertThat(
                testProject.file("build/intermediates/manifests/full/debug/AndroidManifest.xml"))
                .containsAllOf(
                    "package=\"com.example.android.testing.blueprint.test\"",
                    "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                    "android:targetPackage=\"com.android.tests.basic\"")
    }

    @Test
    public void "check instrumentation added"() throws Exception {
        GradleTestProject testProject = project.getSubproject("test")
        project.execute("clean", ":test:assembleDebug")

        assertThat(
                testProject.file("build/intermediates/manifests/full/debug/AndroidManifest.xml"))
                .containsAllOf(
                    "package=\"com.example.android.testing.blueprint.test\"",
                    "<instrumentation",
                    "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                    "android:targetPackage=\"com.android.tests.basic\"")
    }

    @Test
    @Category(DeviceTests.class)
    public void "check will run without instrumentation in manifest"() throws Exception{
        project.execute(":test:deviceCheck")
    }

    @Test
    public void "check model contains tested apks to install"() throws Exception{
        Collection<TestedTargetVariant> toInstall =
                project.executeAndReturnMultiModel("clean")
                        .get(":test").variants.first().getTestedTargetVariants()

        assertThat(toInstall).hasSize(1)
        assertThat(toInstall.first().getTargetProjectPath()).isEqualTo(":app")
        assertThat(toInstall.first().getTargetVariant()).isEqualTo("debug")
    }

    private void addInstrumentationToManifest(){
        GradleTestProject testProject = project.getSubproject("test")
        testProject.file("src/main/AndroidManifest.xml").delete()
        testProject.file("src/main/AndroidManifest.xml") << """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
      package="com.android.tests.basic.test">
      <uses-sdk android:minSdkVersion="16" android:targetSdkVersion="16" />
      <instrumentation android:name="android.test.InstrumentationTestRunner"
                       android:targetPackage="com.android.tests.basic"
                       android:handleProfiling="false"
                       android:functionalTest="false"
                       android:label="Tests for com.android.tests.basic"/>
</manifest>
"""
    }
}
