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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Variant
import org.junit.*

/**
 * Assemble tests for localJars.
 */
class LocalJarsTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("localJars")
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
    void testModel() throws Exception {
        GradleTestProject.SubProjectData libModelData = models.get(":baseLibrary");
        Assert.assertNotNull("Module app null-check", libModelData);
        AndroidProject model = libModelData.model;

        Collection<Variant> variants = model.getVariants();

        Variant releaseVariant = ModelHelper.getVariant(variants, "release");
        Assert.assertNotNull(releaseVariant);

        Dependencies dependencies = releaseVariant.getMainArtifact().getDependencies();
        Assert.assertNotNull(dependencies);

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        Assert.assertNotNull(javaLibraries);

        //  com.google.guava:guava:11.0.2
        //  \--- com.google.code.findbugs:jsr305:1.3.9
        //  + the local jar
        Assert.assertEquals(3, javaLibraries.size());
    }
}
