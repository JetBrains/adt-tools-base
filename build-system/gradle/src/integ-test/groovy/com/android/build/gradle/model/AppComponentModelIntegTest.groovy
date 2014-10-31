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

package com.android.build.gradle.model

import com.android.build.gradle.internal.test.category.DeviceTests
import com.android.build.gradle.internal.test.fixture.GradleProjectTestRule
import com.android.build.gradle.internal.test.fixture.app.HelloWorldApp
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Basic integration test for AppComponentModelPlugin.
 */
class AppComponentModelIntegTest {
    @Rule
    public GradleProjectTestRule fixture = new GradleProjectTestRule();

    @Before
    public void setup() {
        new HelloWorldApp().writeSources(fixture.getSourceDir())
        fixture.buildFile << """
apply plugin: "com.android.model.application"

model {
    android {
        compileSdkVersion $GradleProjectTestRule.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleProjectTestRule.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""
    }

    @Test
    public void basicAssemble() {
        fixture.execute("assembleDebug");
    }

    @Test
    public void flavors() {
        fixture.buildFile << """
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
        fixture.execute(
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
        fixture.execute("connectedAndroidTest");
    }
}
