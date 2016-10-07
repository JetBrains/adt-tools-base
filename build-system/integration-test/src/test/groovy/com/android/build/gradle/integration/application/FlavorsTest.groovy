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
import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.ProductFlavorHelper
import com.android.build.gradle.integration.common.utils.SourceProviderHelper
import com.android.build.gradle.integration.common.utils.VariantHelper
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static com.google.common.truth.Truth.assertThat;

/**
 * Assemble tests for flavors.
 */
@CompileStatic
class FlavorsTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("flavors")
            .create()
    AndroidProject model

    @Test
    void "check flavors show up in model"() throws Exception {
        model = project.executeAndReturnModel("clean", "assembleDebug")

        File projectDir = project.getTestDir()

        assertFalse("Library Project", model.isLibrary())

        assertThat(model.getFlavorDimensions()).containsExactly("group1", "group2")

        ProductFlavorContainer defaultConfig = model.getDefaultConfig()

        new SourceProviderHelper(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test()

        SourceProviderContainer testSourceProviderContainer = ModelHelper.getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST)
        assertNotNull("InstrumentTest source Providers null-check", testSourceProviderContainer)

        new SourceProviderHelper(model.getName(), projectDir,
                ANDROID_TEST.prefix, testSourceProviderContainer.getSourceProvider())
                .test()

        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes()
        assertEquals("Build Type Count", 2, buildTypes.size())

        Collection<Variant> variants = model.getVariants()
        assertEquals("Variant Count", 8, variants.size())

        Collection<ProductFlavorContainer> flavorContainers = model.getProductFlavors();
        assertThat(flavorContainers).hasSize(4);
        Map expected = [f1:"group1", f2: "group1", fa: "group2", fb: "group2"]
        for (ProductFlavorContainer flavorContainer: flavorContainers) {
            ProductFlavor flavor = flavorContainer.getProductFlavor();
            assertEquals(expected.get(flavor.name), flavor.dimension)
        }

        Variant f1faDebugVariant = ModelHelper.getVariant(variants, "f1FaDebug")
        assertThat(f1faDebugVariant.getProductFlavors()).containsExactly("f1","fa")
        new ProductFlavorHelper(f1faDebugVariant.getMergedFlavor(), "F1faDebug Merged Flavor")
                .test()
        new VariantHelper(f1faDebugVariant, projectDir, "flavors-f1-fa-debug.apk").test()
    }

    @Test
    public void "compound source sets are in the model"() throws Exception {
        model = project.executeAndReturnModel("clean", "assembleDebug")

        for (variant in model.variants) {
            assert variant.extraJavaArtifacts.every { it.multiFlavorSourceProvider != null }
            assert variant.extraJavaArtifacts.every { it.variantSourceProvider != null }
            assert variant.extraAndroidArtifacts.every { it.multiFlavorSourceProvider != null }
            // No per-variant source providers for android tests.
            assert variant.extraAndroidArtifacts.every { it.variantSourceProvider == null }
        }
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }

    private void addProductFlavorsVersionNameSuffixes(){
        project.getBuildFile() << """
android{
    defaultConfig {
        versionName '1.0'
    }
    productFlavors {
        f1 {
            versionNameSuffix 'f1'
        }
        f2 {
            versionNameSuffix '-f2'
        }
        fa {
            versionNameSuffix '-fa'
        }
        fb {
            versionNameSuffix 'fb'
        }
    }
}
"""
    }

    private void addBuildTypesVersionNameSuffixes(){
        project.getBuildFile() << """
android{
    defaultConfig {
        versionName '1.0'
    }
    buildTypes {
        release {
            versionNameSuffix 'release'
        }
        debug {
            versionNameSuffix '-debug'
        }
    }
}
"""
    }

    @Test
    public void "check version name with buildTypes only suffix"() throws Exception{
        addBuildTypesVersionNameSuffixes()
        project.execute("clean", "assembleDebug", "assembleRelease")
        TruthHelper.assertThatApk(project.getApk("f1", "fa", "debug")).hasVersionName("1.0-debug")
        TruthHelper.assertThatApk(project.getApk("f1", "fb", "debug")).hasVersionName("1.0-debug")

        TruthHelper.assertThatApk(project.getApk("f1", "fa", "release", "unsigned"))
                .hasVersionName("1.0release")
        TruthHelper.assertThatApk(project.getApk("f1", "fb", "release", "unsigned"))
                .hasVersionName("1.0release")
    }

    @Test
    public void "check version name  with productFlavors only suffix"() throws Exception{
        addProductFlavorsVersionNameSuffixes()
        project.execute("clean", "assembleDebug", "assembleRelease")
        TruthHelper.assertThatApk(project.getApk("f1", "fb", "debug")).hasVersionName("1.0f1fb")
        TruthHelper.assertThatApk(project.getApk("f1", "fa", "debug")).hasVersionName("1.0f1-fa")

        TruthHelper.assertThatApk(project.getApk("f1", "fb", "release", "unsigned"))
                .hasVersionName("1.0f1fb")
        TruthHelper.assertThatApk(project.getApk("f1", "fa", "release", "unsigned"))
                .hasVersionName("1.0f1-fa")
    }

    @Test
    public void "check version name for flavors and build types suffix"() throws Exception{
        addBuildTypesVersionNameSuffixes()
        addProductFlavorsVersionNameSuffixes()

        project.execute("clean", "assembleDebug", "assembleRelease")
        TruthHelper.assertThatApk(project.getApk("f1", "fb", "debug"))
                .hasVersionName("1.0f1fb-debug")
        TruthHelper.assertThatApk(project.getApk("f2", "fb", "debug"))
                .hasVersionName("1.0-f2fb-debug")

        TruthHelper.assertThatApk(project.getApk("f2", "fa", "release", "unsigned"))
                .hasVersionName("1.0-f2-farelease")
        TruthHelper.assertThatApk(project.getApk("f1", "fa", "release", "unsigned"))
                .hasVersionName("1.0f1-farelease")
    }
}
