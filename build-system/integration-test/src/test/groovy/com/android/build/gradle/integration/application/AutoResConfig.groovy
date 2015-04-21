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
import com.google.common.truth.Truth
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Test to ensure that "auto" resConfig setting only package application's languages.
 */
class AutoResConfig {

    static AndroidProject model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("combinedDensityAndLanguagePureSplits")
            .create()

    @BeforeClass
    static void setup() {
        project.getBuildFile() << """android {
              defaultConfig {
                versionCode 12
                minSdkVersion 21
                targetSdkVersion 21
                resConfig "auto"
              }

              splits {
                density {
                    enable false
                }
                language {
                    enable false
                }
              }
              dependencies {

                compile 'com.android.support:appcompat-v7:21.0.3'
                compile 'com.android.support:support-v4:21.0.3'
              }
        }"""
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    public void "test auto resConfigs only package application specific language"()
            throws Exception {

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

        assertEquals(1, debugOutputs.size())
        AndroidArtifactOutput output = debugOutputs.iterator().next()
        assertEquals(1, output.getOutputs().size())

        System.out.println(output.getMainOutputFile().getOutputFile().getAbsolutePath());
        List<String> apkDump = ApkHelper.getApkBadging(output.getMainOutputFile().getOutputFile());
        Truth.assertThat(apkDump).contains("locales: '--_--' 'en' 'fr' 'fr-CA' 'fr-BE'")
    }
}
