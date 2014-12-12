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
import com.android.builder.model.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import static org.junit.Assert.*
/**
 * Assemble tests for artifactApi.
 */
class ArtifactApiTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("artifactApi")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        model = project.getModel()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check metadata info in model"() {
        // check the Artifact Meta Data
        Collection<ArtifactMetaData> extraArtifacts = model.getExtraArtifacts();
        assertNotNull("Extra artifact collection null-check", extraArtifacts);
        assertEquals("Extra artifact size check", 2, extraArtifacts.size());

        assertNotNull("instrument test metadata null-check",
                ModelHelper.getArtifactMetaData(extraArtifacts, ARTIFACT_ANDROID_TEST));

        // get the custom one.
        ArtifactMetaData extraArtifactMetaData = ModelHelper.getArtifactMetaData(
                extraArtifacts, "__test__");
        assertNotNull("custom extra metadata null-check", extraArtifactMetaData);
        assertFalse("custom extra meta data is Test check", extraArtifactMetaData.isTest());
        assertEquals("custom extra meta data type check", ArtifactMetaData.TYPE_JAVA,
                extraArtifactMetaData.getType());
    }

    @Test
    void "check build types contain extra source provider artifact is in model"() {
        // check the extra source provider on the build Types.
        for (BuildTypeContainer btContainer : model.getBuildTypes()) {
            String name = btContainer.getBuildType().getName();
            Collection<SourceProviderContainer> extraSourceProviderContainers = btContainer.getExtraSourceProviders();
            assertNotNull(
                    "Extra source provider containers for build type '" + name + "' null-check",
                    extraSourceProviderContainers);
            assertEquals(
                    "Extra source provider containers for build type size '" + name + "' check",
                    1,
                    extraSourceProviderContainers.size());

            SourceProviderContainer sourceProviderContainer = extraSourceProviderContainers.iterator().next();
            assertNotNull(
                    "Extra artifact source provider for " + name + " null check",
                    sourceProviderContainer);

            assertEquals(
                    "Extra artifact source provider for " + name + " name check",
                    "__test__",
                    sourceProviderContainer.getArtifactName());

            assertEquals(
                    "Extra artifact source provider for " + name + " value check",
                    "buildType:" + name,
                    sourceProviderContainer.getSourceProvider().getManifestFile().getPath());
        }
    }

    @Test
    void "check product flavors contain extra source provider artifact is in model"() {
        // check the extra source provider on the product flavors.
        for (ProductFlavorContainer pfContainer : model.getProductFlavors()) {
            String name = pfContainer.getProductFlavor().getName();
            Collection<SourceProviderContainer> extraSourceProviderContainers = pfContainer.
                    getExtraSourceProviders();
            assertNotNull(
                    "Extra source provider container for product flavor '" + name + "' null-check",
                    extraSourceProviderContainers);
            assertEquals(
                    "Extra artifact source provider container for product flavor size '" + name +
                            "' check",
                    2,
                    extraSourceProviderContainers.size());

            assertNotNull(
                    "Extra source provider container for product flavor '" + name +
                            "': instTest check",
                    ModelHelper.getSourceProviderContainer(extraSourceProviderContainers,
                            ARTIFACT_ANDROID_TEST));


            SourceProviderContainer sourceProviderContainer = ModelHelper.
                    getSourceProviderContainer(
                            extraSourceProviderContainers, "__test__");
            assertNotNull(
                    "Custom source provider container for " + name + " null check",
                    sourceProviderContainer);

            assertEquals(
                    "Custom artifact source provider for " + name + " name check",
                    "__test__",
                    sourceProviderContainer.getArtifactName());

            assertEquals(
                    "Extra artifact source provider for " + name + " value check",
                    "productFlavor:" + name,
                    sourceProviderContainer.getSourceProvider().getManifestFile().getPath());
        }
    }

    @Test
    void "check extra artifact is in variants"() {
        for (Variant variant : model.getVariants()) {
            String name = variant.getName();
            Collection<JavaArtifact> javaArtifacts = variant.getExtraJavaArtifacts();
            assertEquals(1, javaArtifacts.size());
            JavaArtifact javaArtifact = javaArtifacts.iterator().next();
            assertEquals("__test__", javaArtifact.getName());
            assertEquals("assemble:" + name, javaArtifact.getAssembleTaskName());
            assertEquals("compile:" + name, javaArtifact.getCompileTaskName());
            assertEquals(new File("classesFolder:" + name), javaArtifact.getClassesFolder());

            SourceProvider variantSourceProvider = javaArtifact.getVariantSourceProvider();
            assertNotNull(variantSourceProvider);
            assertEquals("provider:" + name, variantSourceProvider.getManifestFile().getPath());

            Dependencies deps = javaArtifact.getDependencies();
            assertNotNull("java artifact deps null-check", deps);
            assertFalse(deps.getJavaLibraries().isEmpty());
        }
    }
}
