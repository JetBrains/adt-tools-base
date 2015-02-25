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
import com.android.builder.model.AndroidProject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file
 * names
 */
class ArchivesBaseNameTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basic")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """

android {
    archivesBaseName = 'random_apk_name'
}
"""
        models = project.getAllModels()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check model failed to load"() {
        File outputFile = models.get(":").getVariants().get(0).getMainArtifact().getOutputs()
                .get(0).getMainOutputFile().getOutputFile()

        assertThat(outputFile.getName().startsWith("random_apk_name"))
    }
}
