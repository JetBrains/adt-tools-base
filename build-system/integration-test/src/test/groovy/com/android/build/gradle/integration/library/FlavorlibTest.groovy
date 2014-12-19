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
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.Variant
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Assemble tests for flavorlib.
 */
class FlavorlibTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("flavorlib")
            .create()
    static Map<String, AndroidProject> models

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
    void report() {
        project.execute("androidDependencies", "signingReport")
    }

    @Test
    void testModel() throws Exception {

        AndroidProject appProject = models.get(":app")
        assertNotNull("Module app null-check", appProject)

        assertFalse("Library Project", appProject.isLibrary())

        Collection<Variant> variants = appProject.getVariants()
        Collection<ProductFlavorContainer> productFlavors = appProject.getProductFlavors()

        ProductFlavorContainer flavor1 = ModelHelper.getProductFlavor(productFlavors, "flavor1")
        assertNotNull(flavor1)

        Variant flavor1Debug = ModelHelper.getVariant(variants, "flavor1Debug")
        assertNotNull(flavor1Debug)

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies()
        assertNotNull(dependencies)
        Collection<AndroidLibrary> libs = dependencies.getLibraries()
        assertNotNull(libs)
        assertEquals(1, libs.size())
        AndroidLibrary androidLibrary = libs.iterator().next()
        assertNotNull(androidLibrary)
        assertEquals(":lib1", androidLibrary.getProject())
        // TODO: right now we can only test the folder name efficiently
        String path = androidLibrary.getFolder().getPath()
        assertTrue(path, path.endsWith("/flavorlib/lib1/unspecified"))

        ProductFlavorContainer flavor2 = ModelHelper.getProductFlavor(productFlavors, "flavor2")
        assertNotNull(flavor2)

        Variant flavor2Debug = ModelHelper.getVariant(variants, "flavor2Debug")
        assertNotNull(flavor2Debug)

        dependencies = flavor2Debug.getMainArtifact().getDependencies()
        assertNotNull(dependencies)
        libs = dependencies.getLibraries()
        assertNotNull(libs)
        Assert.assertEquals(1, libs.size())
        androidLibrary = libs.iterator().next()
        assertNotNull(androidLibrary)
        assertEquals(":lib2", androidLibrary.getProject())
        // TODO: right now we can only test the folder name efficiently
        path = androidLibrary.getFolder().getPath()
        assertTrue(path, path.endsWith("/flavorlib/lib2/unspecified"))
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute("connectedCheck")
    }
}
