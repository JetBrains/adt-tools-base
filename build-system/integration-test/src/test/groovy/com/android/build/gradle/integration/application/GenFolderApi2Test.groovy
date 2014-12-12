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
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for genFolderApi2.
 */
class GenFolderApi2Test {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("genFolderApi2")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.getSingleModel()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check the custom gen folder is present in model"() throws Exception {
        File projectDir = project.getTestDir()

        File buildDir = new File(projectDir, "build")

        for (Variant variant : model.getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact()
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo)

            // get the generated source folders.
            Collection<File> genFolder = mainInfo.getGeneratedSourceFolders()

            // We're looking for a custom folder
            String folderStart = new File(buildDir, "customCode").getAbsolutePath() + File.separatorChar
            boolean found = false
            for (File f : genFolder) {
                if (f.getAbsolutePath().startsWith(folderStart)) {
                    found = true
                    break
                }
            }

            assertTrue("custom generated source folder check", found)
        }
    }
}
