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
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Test separate test module testing an app with aar dependencies.
 */
@CompileStatic
public class SeparateTestWithAarDependencyTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModule")
            .create()

    static Map<String, AndroidProject> models

    @BeforeClass
    static void setup() {
        project.getSubproject("app").getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion = '$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION'

    publishNonDefault true

    defaultConfig {
        minSdkVersion 8
    }
}
dependencies {
    compile 'com.android.support:appcompat-v7:22.1.0'
}
        """

        models = project.executeAndReturnMultiModel("clean", "assemble")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check app doesn't contain test app's code"() {
        File apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainClass("Lcom/android/tests/basic/Main;")
    }

    @Test
    void "check app doesn't contain test app's layout"() {
        File apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainResource("layout/main.xml")
    }

    @Test
    void "check app doesn't contain test app's dependency lib's code"() {
        File apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainClass("Landroid/support/v7/app/ActionBar;")
    }

    @Test
    void "check app doesn't contain test app's dependency lib's resources"() {
        File apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainResource("layout/abc_action_bar_title_item.xml")
    }

    @Test
    void "check test model includes the tested app"() {
        Collection<Variant> variants = models.get(":test").getVariants()

        // get the main artifact of the debug artifact and its dependencies
        Variant variant = ModelHelper.getVariant(variants, "debug")
        AndroidArtifact artifact = variant.getMainArtifact()
        Dependencies dependencies = artifact.getDependencies()

        // check the app project shows up as a project dependency
        Collection<String> projects = dependencies.getProjects()
        assertThat(projects).containsExactly(":app")

        // and that nothing else shows up.
        // TODO: fix this.
//        Collection<JavaLibrary> javaLibs = dependencies.getJavaLibraries();
//        assertThat(javaLibs).hasSize(0);
//        Collection<AndroidLibrary> libs = dependencies.getLibraries();
//        assertThat(libs).hasSize(0);
    }

    @Test
    @Category(DeviceTests)
    void "connected check"() {
        GradleTestProject.assumeLocalDevice()
        project.execute("clean",":test:connectedCheck");
    }
}
