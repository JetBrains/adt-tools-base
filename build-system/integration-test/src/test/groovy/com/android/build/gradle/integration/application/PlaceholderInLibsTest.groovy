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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Test for unresolved placeholders in libraries.
 */
class PlaceholderInLibsTest {
    static Map<String, AndroidProject> models

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("placeholderInLibsTest")
            .create()

    @BeforeClass
    static void setup() {
        models = project.executeAndReturnMultiModel("clean",
                ":examplelibrary:generateDebugAndroidTestSources", "app:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    public void "test library placeholder substitution in final apk"() throws Exception {

        // Load the custom model for the project
        Collection<Variant> variants = models.get(":app").getVariants()
        assertEquals("Variant Count", 2 , variants.size())

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, "flavorDebug")
        assertNotNull("debug Variant null-check", debugVariant)
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact()
        assertNotNull("Debug main info null-check", debugMainArtifact)

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs()
        assertNotNull(debugOutputs)

        assertEquals(1, debugOutputs.size())
        AndroidArtifactOutput output = debugOutputs.iterator().next()
        assertEquals(1, output.getOutputs().size())

        List<String> apkBadging =
                ApkHelper.getApkBadging(output.getOutputs().getAt(0).getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'")) {
                return;
            }
        }
        Assert.fail("failed to find the permission with the right placeholder substitution.")
    }
}
