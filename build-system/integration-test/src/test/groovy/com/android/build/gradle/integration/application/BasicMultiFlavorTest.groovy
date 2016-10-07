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
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.SourceProviderHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.Variant
import com.android.utils.StringHelper
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static com.google.common.truth.Truth.assertThat
/**
 * Assemble tests for basicMultiFlavors
 */
@CompileStatic
class BasicMultiFlavorTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("basicMultiFlavors")
            .create()

    @Test
    void "check source providers"() {
        AndroidProject model = project.model().getSingle()
        File projectDir = project.getTestDir()
        ModelHelper.testDefaultSourceSets(model, projectDir)

        // test the source provider for the flavor
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors()
        assertThat(productFlavors).hasSize(4)

        for (ProductFlavorContainer pfContainer : productFlavors) {
            String name = pfContainer.getProductFlavor().getName()
            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    name,
                    pfContainer.getSourceProvider())
                    .test()

            // Unit tests and android tests.
            assertThat(pfContainer.getExtraSourceProviders()).hasSize(2)
            SourceProviderContainer container = ModelHelper.getSourceProviderContainer(
                    pfContainer.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST)
            assertThat(container).isNotNull()

            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    ANDROID_TEST.prefix + StringHelper.capitalize(name),
                    container.getSourceProvider())
                    .test()
        }

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact()
            assertThat(artifact.getVariantSourceProvider()).isNotNull()
            assertThat(artifact.getMultiFlavorSourceProvider()).isNotNull()
        }
    }

    @Test
    void "check precedence for multi-flavor"() {
        project.execute("assembleFreeBetaDebug")

        // Make sure "beta" overrides "free" and "defaultConfig".
        assertThatApk(project.getApk("free", "beta", "debug")).hasMaxSdkVersion(19)

        // Make sure the suffixes are applied in the right order.
        assertThatApk(project.getApk("free", "beta", "debug"))
                .hasVersionName("com.example.default.free.beta.debug")
    }

    @Test
    void "check res values and manifest placeholders for multi-flavor"() {
        addResValuesAndPlaceholders()
        AndroidProject model = project.executeAndReturnModel("assembleFreeBetaDebug")

        Variant variant = ModelHelper.findVariantByName(model.getVariants(), "freeBetaDebug")

        assertThat(variant.mergedFlavor.resValues.get("VALUE_DEBUG").value)
                .isEqualTo("13") // Value from "beta".

        assertThat(variant.mergedFlavor.manifestPlaceholders.get("holder")).isEqualTo("beta")
    }

    @Test
    void "check resources resolution"() {
        project.execute("assembleFreeBetaDebug")
        assertThatAar(project.getApk("free", "beta", "debug"))
                .containsResource("drawable/free.png")
    }

    private void addResValuesAndPlaceholders() {
        project.getBuildFile() << """
android {
    productFlavors {
        free {
            resValue "string", "VALUE_DEBUG",   "10"
            manifestPlaceholders = ["holder":"free"]
        }
        beta {
            resValue "string", "VALUE_DEBUG",   "13"
            manifestPlaceholders = ["holder":"beta"]
        }
    }
}
"""
    }
}
