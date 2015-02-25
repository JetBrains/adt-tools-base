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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Basic integration test for LibraryComponentModelPlugin.
 */
class LibraryComponentPluginTest {
    private static testApp = new HelloWorldLibraryApp()

    static {
        AndroidTestApp app = (AndroidTestApp) testApp.getSubproject(":app")
        app.addFile(new TestSourceFile("", "build.gradle",
"""
apply plugin: "com.android.model.application"

configurations {
    compile
}

dependencies {
    compile project(":lib")
}

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""))

        AndroidTestApp lib = (AndroidTestApp) testApp.getSubproject(":lib")
        lib.addFile(new TestSourceFile("", "build.gradle",
"""
apply plugin: "com.android.model.library"

model {
    android.config {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
}
"""))
    }

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(testApp)
            .forExpermimentalPlugin(true)
            .create();

    @AfterClass
    static void cleanUp() {
        project = null
        testApp = null
    }

    @Test
    void assemble() {
        project.execute("assemble")
    }
}
