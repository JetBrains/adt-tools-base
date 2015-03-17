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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.AndroidProject
import com.android.builder.model.ArtifactMetaData
import com.android.builder.model.JavaArtifact
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST
import static com.google.common.truth.Truth.assertThat
/**
 * Tests for the unit-tests related parts of the builder model.
 */
class UnitTestingModelTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .create();

    @Before
    public void setUp() {
        project.buildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void "Unit testing artifacts are included in the model"() {
        AndroidProject model = project.singleModel

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

            assertThat(variant.mainArtifact.javaResourcesFolder.path)
                    .endsWith("intermediates/javaResources/" + variant.name)
            assertThat(unitTestArtifact.javaResourcesFolder.path)
                    .endsWith("intermediates/javaResources/test/" + variant.name)
        }

        def sourceProvider = model.defaultConfig
                .extraSourceProviders
                .find { it.artifactName == ARTIFACT_UNIT_TEST }
                .sourceProvider

        assertThat(sourceProvider.javaDirectories).hasSize(1)
        assertThat(sourceProvider.javaDirectories.first().absolutePath).endsWith("test/java")
    }

    @Test
    public void flavors() throws Exception {
        project.buildFile << """
android {
    productFlavors { paid; free }
}
"""

        AndroidProject model = project.singleModel

        assertThat(model.productFlavors).hasSize(2)

        for (flavor in model.productFlavors) {
            def sourceProvider = flavor.extraSourceProviders
                    .find { it.artifactName == ARTIFACT_UNIT_TEST }
                    .sourceProvider

            assertThat(sourceProvider.javaDirectories).hasSize(1)
            assertThat(sourceProvider.javaDirectories.first().absolutePath)
                    .endsWith("test${flavor.productFlavor.name.capitalize()}/java")
        }
    }
}
