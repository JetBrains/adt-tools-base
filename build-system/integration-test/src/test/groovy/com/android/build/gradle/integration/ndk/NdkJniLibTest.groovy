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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Assemble tests for ndkJniLib.
 */
class NdkJniLibTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("ndkJniLib")
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
    void lint() {
        project.execute("lint")
    }

    @Test
    void "check version code"() {
        GradleTestProject app = project.getSubproject("app")
        ApkHelper.checkVersion(app.getApk("gingerbread", "universal", "debug"),        1000123)
        ApkHelper.checkVersion(app.getApk("gingerbread", "armeabi-v7a", "debug"),      1100123)
        ApkHelper.checkVersion(app.getApk("gingerbread", "mips", "debug"),             1200123)
        ApkHelper.checkVersion(app.getApk("gingerbread", "x86", "debug"),              1300123)
        ApkHelper.checkVersion(app.getApk("icecreamSandwich", "universal", "debug"),   2000123)
        ApkHelper.checkVersion(app.getApk("icecreamSandwich", "armeabi-v7a", "debug"), 2100123)
        ApkHelper.checkVersion(app.getApk("icecreamSandwich", "mips", "debug"),        2200123)
        ApkHelper.checkVersion(app.getApk("icecreamSandwich", "x86", "debug"),         2300123)
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
