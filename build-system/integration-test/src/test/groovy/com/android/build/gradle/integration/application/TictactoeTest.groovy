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
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.core.BuilderConstants.DEBUG
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
/**
 * Assemble tests for tictactoe.
 */
@CompileStatic
class TictactoeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("tictactoe")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        models = project.executeAndReturnMultiModel("clean", "assembleDebug")
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
    public void testModel() throws Exception {
        AndroidProject libModel = models.get(":lib")
        assertNotNull("lib module model null-check", libModel)
        assertTrue("lib module library flag", libModel.isLibrary())

        AndroidProject appModel = models.get(":app")
        assertNotNull("app module model null-check", appModel)

        Collection<Variant> variants = appModel.getVariants()
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        assertNotNull("debug variant null-check", debugVariant)

        Dependencies dependencies = debugVariant.getMainArtifact().getDependencies()
        assertNotNull(dependencies)

        Collection<AndroidLibrary> libs = dependencies.getLibraries()
        assertNotNull(libs)
        assertEquals(1, libs.size())

        AndroidLibrary androidLibrary = libs.iterator().next()
        assertNotNull(androidLibrary)

        assertEquals("Dependency project path", ":lib", androidLibrary.getProject())

        // TODO: right now we can only test the folder name efficiently
        String path = androidLibrary.getFolder().getPath()
        assertTrue(path, path.endsWith("/tictactoe/lib/unspecified"))
    }
}
