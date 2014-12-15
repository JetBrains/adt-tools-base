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
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for renamedApk.
 */
class RenamedApkTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("renamedApk")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean", "assembleDebug");
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void "check model reflects renamed apk"() throws Exception {
        File projectDir = project.getTestDir()

        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        File buildDir = new File(projectDir, "build")

        for (Variant variant : variants) {
            AndroidArtifact mainInfo = variant.getMainArtifact()
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo)

            AndroidArtifactOutput output = mainInfo.getOutputs().iterator().next()

            assertEquals("Output file for " + variant.getName(),
                    new File(buildDir, variant.getName() + ".apk"),
                    output.getMainOutputFile().getOutputFile())
        }
    }

    @Test
    void "check renamed apk"() {
        File debugApk = project.file("build/debug.apk")
        assertTrue("Check output file: " + debugApk, debugApk.isFile())
    }
}
