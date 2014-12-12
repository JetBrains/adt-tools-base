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



package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * Assemble tests for ndkJniPureSplitLib.
 */
class NdkJniPureSplitLibTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("ndkJniPureSplitLib")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", ":app:assembleDebug");
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
        ApkHelper.checkVersion(app.getApk("free", "debug_armeabi-v7a"), 123)
        ApkHelper.checkVersion(app.getApk("free", "debug_mips"),        123)
        ApkHelper.checkVersion(app.getApk("free", "debug_x86"),         123)
        ApkHelper.checkVersion(app.getApk("paid", "debug_armeabi-v7a"), 123)
        ApkHelper.checkVersion(app.getApk("paid", "debug_mips"),        123)
        ApkHelper.checkVersion(app.getApk("paid", "debug_x86"),         123)
    }
}
