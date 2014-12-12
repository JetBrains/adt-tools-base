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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.*
import org.junit.*
import org.junit.experimental.categories.Category

/**
 * Assemble tests for flavoredlib.
 */
class FlavoredlibTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("flavoredlib")
            .create()
    static Map<String, GradleTestProject.SubProjectData> models

    @BeforeClass
    static void setUp() {
        models = project.executeAndReturnMultiModel("clean", "assembleDebug");
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void testModel() {
        GradleTestProject.SubProjectData appModelData = models.get(":app")
        Assert.assertNotNull("Module app null-check", appModelData)
        AndroidProject model = appModelData.getModel()

        Assert.assertFalse("Library Project", model.isLibrary())

        Collection<Variant> variants = model.getVariants()
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors()

        ProductFlavorContainer flavor1 = ModelHelper.getProductFlavor(productFlavors, "flavor1")
        Assert.assertNotNull(flavor1)

        Variant flavor1Debug = ModelHelper.getVariant(variants, "flavor1Debug")
        Assert.assertNotNull(flavor1Debug)

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies()
        Assert.assertNotNull(dependencies)
        Collection<AndroidLibrary> libs = dependencies.getLibraries()
        Assert.assertNotNull(libs)
        Assert.assertEquals(1, libs.size())
        AndroidLibrary androidLibrary = libs.iterator().next()
        Assert.assertNotNull(androidLibrary)
        Assert.assertEquals(":lib", androidLibrary.getProject())
        Assert.assertEquals("flavor1Release", androidLibrary.getProjectVariant())
        // TODO: right now we can only test the folder name efficiently
        String path = androidLibrary.getFolder().getPath()
        Assert.assertTrue(path, path.endsWith("/project/lib/unspecified/flavor1Release"))

        ProductFlavorContainer flavor2 = ModelHelper.getProductFlavor(productFlavors, "flavor2")
        Assert.assertNotNull(flavor2)

        Variant flavor2Debug = ModelHelper.getVariant(variants, "flavor2Debug")
        Assert.assertNotNull(flavor2Debug)

        dependencies = flavor2Debug.getMainArtifact().getDependencies()
        Assert.assertNotNull(dependencies)
        libs = dependencies.getLibraries()
        Assert.assertNotNull(libs)
        Assert.assertEquals(1, libs.size())
        androidLibrary = libs.iterator().next()
        Assert.assertNotNull(androidLibrary)
        Assert.assertEquals(":lib", androidLibrary.getProject())
        Assert.assertEquals("flavor2Release", androidLibrary.getProjectVariant())
        // TODO: right now we can only test the folder name efficiently
        path = androidLibrary.getFolder().getPath()
        Assert.assertTrue(path, path.endsWith("/project/lib/unspecified/flavor2Release"))
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
