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
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.JavaArtifact
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST
/**
 * Tests for the unit-tests related parts of the builder model.
 */
@CompileStatic
class UnitTestingModelTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("unitTestingComplexProject")
            .create();

    @Test
    public void "Unit testing artifacts are included in the model"() {
        // Build the project, so we can verify paths in the model exist.
        project.execute("test")

        AndroidProject model = project.allModels[":app"]

        assertThat(model.extraArtifacts*.name).containsExactly(
                AndroidProject.ARTIFACT_ANDROID_TEST,
                ARTIFACT_UNIT_TEST)

        def unitTestMetadata = model.extraArtifacts.find { it.name == ARTIFACT_UNIT_TEST }

        assert unitTestMetadata.isTest()
        assert unitTestMetadata.type == ArtifactMetaData.TYPE_JAVA

        for (variant in model.variants) {
            def unitTestArtifacts = variant.extraJavaArtifacts.findAll {
                it.name == ARTIFACT_UNIT_TEST
            }
            assert unitTestArtifacts.size() == 1

            JavaArtifact unitTestArtifact = unitTestArtifacts.first()
            assert unitTestArtifact.name == ARTIFACT_UNIT_TEST
            assertThat(unitTestArtifact.assembleTaskName).contains("UnitTest")
            assertThat(unitTestArtifact.assembleTaskName).contains(variant.name.capitalize())
            assertThat(unitTestArtifact.compileTaskName).contains("UnitTest")
            assertThat(unitTestArtifact.compileTaskName).contains(variant.name.capitalize())

            // No per-variant source code.
            assertThat(unitTestArtifact.variantSourceProvider).isNull()
            assertThat(unitTestArtifact.multiFlavorSourceProvider).isNull()

            assertThat(variant.mainArtifact.classesFolder).isDirectory()
            assertThat(variant.mainArtifact.javaResourcesFolder).isDirectory()
            assertThat(unitTestArtifact.classesFolder).isDirectory()
            assertThat(unitTestArtifact.javaResourcesFolder).isDirectory()

            assertThat(unitTestArtifact.classesFolder)
                    .isNotEqualTo(variant.mainArtifact.classesFolder)
            assertThat(unitTestArtifact.javaResourcesFolder)
                    .isNotEqualTo(variant.mainArtifact.javaResourcesFolder)
        }

        def sourceProvider = model.defaultConfig
                .extraSourceProviders
                .find { it.artifactName == ARTIFACT_UNIT_TEST }
                .sourceProvider

        assertThat(sourceProvider.javaDirectories).hasSize(1)
        assertThat(sourceProvider.javaDirectories.first().absolutePath).endsWith(
                FileUtils.join("test", "java"))
    }

    @Test
    public void flavors() throws Exception {
        project.getSubproject("app").buildFile << """
android {
    productFlavors { paid; free }
}
"""
        AndroidProject model = project.allModels[":app"]

        assertThat(model.productFlavors).hasSize(2)

        for (flavor in model.productFlavors) {
            def sourceProvider = flavor.extraSourceProviders
                    .find { it.artifactName == ARTIFACT_UNIT_TEST }
                    .sourceProvider

            assertThat(sourceProvider.javaDirectories).hasSize(1)
            assertThat(sourceProvider.javaDirectories.first().absolutePath)
                    .endsWith("test${flavor.productFlavor.name.capitalize()}" +
                            "${File.separatorChar}java")
        }
    }
}
