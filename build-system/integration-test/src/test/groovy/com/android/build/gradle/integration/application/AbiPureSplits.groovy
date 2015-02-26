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

package com.android.build.gradle.integration.application

import com.android.build.OutputFile
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.google.common.collect.Sets
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk


/**
 * Test drive for the abiPureSplits samples test.
 */
class AbiPureSplits {
    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("abiPureSplits")
            .create()

    @BeforeClass
    static void setup() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    public void "test abi pure splits"() throws Exception {

        // Load the custom model for the project
        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        assertNotNull("debug Variant null-check", debugVariant)
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs()
        assertNotNull(debugOutputs)

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5)
        expected.add("mips")
        expected.add("x86")
        expected.add("armeabi-v7a")

        assertEquals(1, debugOutputs.size())
        AndroidArtifactOutput output = debugOutputs.iterator().next()
        assertEquals(4, output.getOutputs().size())
        for (OutputFile outputFile : output.getOutputs()) {
            String filter = ModelHelper.getFilter(outputFile, OutputFile.ABI)
            assertEquals(filter == null  ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType())

            // with pure splits, all split have the same version code.
            assertEquals(123, output.getVersionCode())
            if (filter != null) {
                expected.remove(filter)

                if (outputFile.getFilterTypes().contains(OutputFile.ABI)) {
                    // if this is an ABI split, ensure the .so file presence (and only one)
                    assertThatZip(outputFile.getOutputFile()).entries("lib/.*")
                            .containsExactly("lib/" + filter + "/libhello-jni.so");
                }

            } else {
                // main file should not have any lib/ entries.
                assertThatZip(outputFile.getOutputFile()).entries("lib/.*").isEmpty()
                // assert that our resources got packaged in the main file.
                assertThatZip(outputFile.getOutputFile()).entries("res/.*").hasSize(5)
            }
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty())
    }
}
