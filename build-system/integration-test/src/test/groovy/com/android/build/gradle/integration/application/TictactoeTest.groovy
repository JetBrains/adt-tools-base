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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.SubProjectData
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import org.junit.*

import static com.android.builder.core.BuilderConstants.DEBUG
/**
 * Assemble tests for tictactoe.
 */
class TictactoeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("tictactoe")
            .create()
    static Map<String, SubProjectData> models

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
    public void testModel() throws Exception {
        SubProjectData libModelData = models.get(":lib")

        Assert.assertNotNull("lib module model null-check", libModelData)
        Assert.assertTrue("lib module library flag", libModelData.model.isLibrary())

        SubProjectData appModelData = models.get(":app")
        Assert.assertNotNull("app module model null-check", appModelData)

        Collection<Variant> variants = appModelData.model.getVariants()
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG)
        Assert.assertNotNull("debug variant null-check", debugVariant)

        Dependencies dependencies = debugVariant.getMainArtifact().getDependencies()
        Assert.assertNotNull(dependencies)

        Collection<AndroidLibrary> libs = dependencies.getLibraries()
        Assert.assertNotNull(libs)
        Assert.assertEquals(1, libs.size())

        AndroidLibrary androidLibrary = libs.iterator().next()
        Assert.assertNotNull(androidLibrary)

        Assert.assertEquals("Dependency project path", ":lib", androidLibrary.getProject())

        // TODO: right now we can only test the folder name efficiently
        String path = androidLibrary.getFolder().getPath();
        Assert.assertTrue(path, path.endsWith("/project/lib/unspecified"))
    }
}
