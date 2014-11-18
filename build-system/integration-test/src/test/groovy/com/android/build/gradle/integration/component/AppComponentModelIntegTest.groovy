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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Basic integration test for AppComponentModelPlugin.
 */
class AppComponentModelIntegTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder().create();

    @Before
    public void setup() {
        new HelloWorldApp().writeSources(project.testDir)
        project.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""
    }

    @Test
    public void basicAssemble() {
        project.execute("assembleDebug");
    }

    @Test
    public void flavors() {
        project.buildFile << """
model {
    android.buildTypes {
        b1
    }
    android.productFlavors {
        f1
        f2
    }
}
"""
        // Runs all assemble tasks and ensure all combinations of assemble* tasks are created.
        project.execute(
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
                "assembleTest",
                "assembleF1DebugTest",
                "assembleF2DebugTest");
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() {
        project.execute("connectedAndroidTest");
    }
}
