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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.google.common.io.Files
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

/**
 * Assemble tests for multiDex.
 */
class MultiDexTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("multiDex")
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
    void "check classes.dex"() {
        // manually inspcet the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        File classesDex = project.file("build/" + FD_INTERMEDIATES + "/dex/ics/debug/classes.dex")
        File apk = project.getApk("ics", "debug")

        ZipHelper.checkContent(apk, "classes.dex", Files.toByteArray(classesDex))
    }

    @Test
    void "check multidex without obfuscate"() {
        project.execute("assembleIcsProguard")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
