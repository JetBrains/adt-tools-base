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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for rsSupportMode.
 */
class RsSupportModeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("rsSupportMode")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model =project.executeAndReturnModel("clean", "assembleDebug")
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
    void testRsSupportMode() throws Exception {
        Variant debugVariant = ModelHelper.getVariant(model.getVariants(), "x86Debug")
        assertNotNull("x86Debug variant null-check", debugVariant)

        AndroidArtifact mainArtifact = debugVariant.getMainArtifact()
        Dependencies dependencies = mainArtifact.getDependencies()

        assertFalse(dependencies.getJavaLibraries().isEmpty())

        boolean foundSupportJar = false
        for (JavaLibrary lib : dependencies.getJavaLibraries()) {
            File file = lib.getJarFile()
            if (SdkConstants.FN_RENDERSCRIPT_V8_JAR.equals(file.getName())) {
                foundSupportJar = true
                break
            }
        }

        assertTrue("Found suppport jar check", foundSupportJar)
    }
}
