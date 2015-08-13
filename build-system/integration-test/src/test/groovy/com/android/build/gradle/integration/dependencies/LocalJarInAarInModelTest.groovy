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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.Variant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertTrue
/**
 * test for the path of the local jars in aars before and after exploding them.
 */
class LocalJarInAarInModelTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldApp())
            .create()

    @Before
    void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
  compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
  buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"

  defaultConfig {
    minSdkVersion 4
  }
}

dependencies {
  compile 'com.android.support:support-v4:22.1.1'
}
"""
    }

    @After
    void cleanUp() {
        project = null
    }

    @Test
    void checkModelBeforeBuild() {
        //clean the project and get the model. The aar won't be exploded for this sync event.
        AndroidProject model = project.executeAndReturnModel("clean")

        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug")
        Dependencies dependencies = variant.getMainArtifact().getDependencies()
        Collection<AndroidLibrary> libraries = dependencies.getLibraries();

        TruthHelper.assertThat(libraries).hasSize(1);

        // now build the project.
        project.execute("prepareDebugDependencies")

        // now check the model validity
        AndroidLibrary lib = libraries.iterator().next()

        File jarFile = lib.getJarFile()
        assertTrue("File doesn't exist: " + jarFile, jarFile.exists());
        for (File localJar : lib.getLocalJars()) {
            assertTrue("File doesn't exist: " + localJar, localJar.exists());
        }
    }

    @Test
    void checkModelAfterBuild() {
        //build the project and get the model. The aar is exploded for this sync event.
        AndroidProject model = project.executeAndReturnModel("clean", "prepareDebugDependencies")

        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug")
        Dependencies dependencies = variant.getMainArtifact().getDependencies()
        Collection<AndroidLibrary> libraries = dependencies.getLibraries();

        TruthHelper.assertThat(libraries).hasSize(1);

        // now check the model validity
        AndroidLibrary lib = libraries.iterator().next()

        File jarFile = lib.getJarFile()
        assertTrue("File doesn't exist: " + jarFile, jarFile.exists());
        for (File localJar : lib.getLocalJars()) {
            assertTrue("File doesn't exist: " + localJar, localJar.exists());
        }
    }
}
