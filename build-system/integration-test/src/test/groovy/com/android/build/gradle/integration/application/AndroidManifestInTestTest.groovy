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
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.ide.common.process.ProcessInfoBuilder
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.fail
/**
 * Assemble tests for androidManifestInTest.
 */
@CompileStatic
class AndroidManifestInTestTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("androidManifestInTest")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleDebugAndroidTest")
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
    public void testUserProvidedTestAndroidManifest() throws Exception {
        File testApk = project.getApk("debug", "androidTest", "unaligned")

        ProcessInfoBuilder builder = new ProcessInfoBuilder()
        builder.setExecutable(SdkHelper.getAapt())

        builder.addArgs("l", "-a", testApk.getPath())

        List<String> output = ApkHelper.runAndGetOutput(builder.createProcess())

        System.out.println("Beginning dump");
        boolean foundPermission = false;
        boolean foundMetadata = false;
        for (String line : output) {
            if (line.contains("foo.permission-group.COST_MONEY")) {
                foundPermission = true
            }
            if (line.contains("meta-data")) {
                foundMetadata = true
            }
        }
        if (!foundPermission) {
            fail("Could not find user-specified permission group.")
        }
        if (!foundMetadata) {
            fail("Could not find meta-data under instrumentation ")
        }
    }
}
