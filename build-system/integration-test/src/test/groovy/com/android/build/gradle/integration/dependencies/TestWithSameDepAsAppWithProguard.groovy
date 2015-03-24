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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Tests the handling of test dependency.
 */
class TestWithSameDepAsAppWithProguard {

    private static AndroidTestApp testApp = new HelloWorldApp()

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(testApp)
            .create()

    @BeforeClass
    public static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

repositories {
  jcenter()
}

android {
  compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
  buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

  defaultConfig {
    minSdkVersion 21
  }

  buildTypes {
    debug {
      minifyEnabled true
      proguardFiles getDefaultProguardFile('proguard-android.txt')
      }
  }
}

dependencies {
  compile 'com.android.tools:annotations:24.1.0'
  androidTestCompile 'com.android.tools:annotations:24.1.0'
}
"""

    }

    @AfterClass
    public static void cleanUp() {
        project = null
    }

    @Test
    public void "Test proguard on test variant succeeds"() {
        project.execute("clean", "assembleDebugAndroidTest")
    }
}
