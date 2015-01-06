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

import com.android.build.OutputFile
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.ZipHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.google.common.collect.Maps
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for densitySplit.
 */
class DensitySplitTest {

    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("densitySplit")
            .create()

    @BeforeClass
    static void setUp() {
        model = project.executeAndReturnModel("clean", "assembleDebug")
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
    void testPackaging() {
        for (Variant variant : model.getVariants()) {
            AndroidArtifact mainArtifact = variant.getMainArtifact()
            if (!variant.getBuildType().equalsIgnoreCase("Debug")) {
                continue
            }
            assertEquals(5, mainArtifact.getOutputs().size())

            for (AndroidArtifactOutput output : mainArtifact.getOutputs()) {
                ZipHelper.checkFileExists(
                        output.getMainOutputFile().getOutputFile(),
                        "res/drawable-mdpi-v4/other.png")
            }
        }
    }

    @Test
    void "check version code in apk"() {
        ApkHelper.checkVersion(project.getApk("universal", "debug"), 112, "version 112")
        ApkHelper.checkVersion(project.getApk("mdpi", "debug"), 212, "version 212")
        ApkHelper.checkVersion(project.getApk("hdpi", "debug"), 312, "version 312")
        ApkHelper.checkVersion(project.getApk("xhdpi", "debug"), 412, "version 412")
        ApkHelper.checkVersion(project.getApk("xxhdpi", "debug"), 512, "version 512")
    }

    @Test
    void "check version code in model"() {
        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        assertNotNull("debug Variant null-check", debugVariant)
        AndroidArtifact debugMainArficat = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArficat)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArficat.getOutputs()
        assertNotNull(debugOutputs)
        assertEquals(5, debugOutputs.size())

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5)
        expected.put(null, 112)
        expected.put("mdpi", 212)
        expected.put("hdpi", 312)
        expected.put("xhdpi", 412)
        expected.put("xxhdpi", 512)

        assertEquals(5, debugOutputs.size())
        for (AndroidArtifactOutput output : debugOutputs) {
            assertEquals(OutputFile.FULL_SPLIT, output.getMainOutputFile().getOutputType())
            Collection<? extends OutputFile> outputFiles = output.getOutputs()
            assertEquals(1, outputFiles.size())
            assertNotNull(output.getMainOutputFile())

            String densityFilter = ModelHelper.getFilter(output.getMainOutputFile(), OutputFile.DENSITY)
            Integer value = expected.get(densityFilter)
            // this checks we're not getting an unexpected output.
            assertNotNull("Check Valid output: " + (densityFilter == null ? "universal"
                    : densityFilter),
                    value)

            assertEquals(value.intValue(), output.getVersionCode())
            expected.remove(densityFilter)
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty())
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
