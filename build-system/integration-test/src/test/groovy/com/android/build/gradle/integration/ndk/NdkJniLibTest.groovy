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
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for ndkJniLib.
 */
@CompileStatic
class NdkJniLibTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniLib")
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
        
        assertThatApk(app.getApk("gingerbread", "universal", "debug")).hasVersionCode(1000123)
        assertThatApk(app.getApk("gingerbread", "armeabi-v7a", "debug")).hasVersionCode(1100123)
        assertThatApk(app.getApk("gingerbread", "mips", "debug")).hasVersionCode(1200123)
        assertThatApk(app.getApk("gingerbread", "x86", "debug")).hasVersionCode(1300123)
        assertThatApk(app.getApk("icecreamSandwich", "universal", "debug")).hasVersionCode(2000123)
        assertThatApk(app.getApk("icecreamSandwich", "armeabi-v7a", "debug")).hasVersionCode(2100123)
        assertThatApk(app.getApk("icecreamSandwich", "mips", "debug")).hasVersionCode(2200123)
        assertThatApk(app.getApk("icecreamSandwich", "x86", "debug")).hasVersionCode(2300123)
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
