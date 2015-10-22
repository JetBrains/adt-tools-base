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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import groovy.transform.CompileStatic
import com.android.builder.model.AndroidProject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.google.common.truth.Truth.assertThat

/**
 * Basic integration test for AppComponentModelPlugin.
 */
@Category(SmokeTests.class)
@CompileStatic
class AppComponentPluginTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .forExperimentalPlugin(true)
            .withoutNdk()
            .create();

    @Before
    public void setUp() {
        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion = $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion = "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""
    }

    @Test
    public void basicAssemble() {
        AndroidProject model = project.executeAndReturnModel("assemble");
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.name)
        assertThat(model.getBuildTypes()).hasSize(2)
        assertThat(model.getProductFlavors()).hasSize(0)
        assertThat(model.getVariants()).hasSize(2)
    }

    @Test
    public void flavors() {
        project.buildFile << """
model {
    android.buildTypes {
        create("b1")
    }
    android.productFlavors {
        create("f1")
        create("f2")
    }
}
"""
        // Ensure all combinations of assemble* tasks are created.
        List<String> tasks = project.getTaskList()
        assertThat(tasks).containsAllOf(
                "assemble",
                "assembleB1",
                "assembleDebug",
                "assembleF1",
                "assembleF1B1",
                "assembleF1Debug",
                "assembleF1Release",
                "assembleF2",
                "assembleF2B1",
                "assembleF2Debug",
                "assembleF2Release",
                "assembleRelease",
                "assembleAndroidTest",
                "assembleF1DebugAndroidTest",
                "assembleF2DebugAndroidTest");

        AndroidProject model = project.executeAndReturnModel("assemble");
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(project.name)
        assertThat(model.getBuildTypes()).hasSize(3)
        assertThat(model.getProductFlavors()).hasSize(2)
        assertThat(model.getVariants()).hasSize(6)
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() {
        project.executeConnectedCheck();
    }
}
