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

import com.android.annotations.NonNull
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

/**
 * test for flavored dependency on a different package.
 */
@CompileStatic
class AppWithResolutionStrategyForAarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
subprojects {
    apply from: "\$rootDir/../commonLocalRepo.gradle"
}
"""
        project.getSubproject('app').getBuildFile() << """

dependencies {
    debugCompile project(':library')
    releaseCompile project(':library')
}

configurations { _debugCompile }

configurations._debugCompile {
  resolutionStrategy {
    eachDependency { DependencyResolveDetails details ->
      if (details.requested.name == 'jdeferred-android-aar') {
        details.useVersion '1.2.2'
      }
    }
  }
}

"""

        project.getSubproject('library').getBuildFile() << """

dependencies {
    compile 'org.jdeferred:jdeferred-android-aar:1.2.3'
}
"""

        models = project.getAllModels()
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check model contain correct dependencies"() {
        AndroidProject appProject = models.get(':app')
        Collection<Variant> appVariants = appProject.getVariants()

        checkJarDependency(appVariants, 'debug', 'org.jdeferred:jdeferred-android-aar:aar:1.2.2')
        checkJarDependency(appVariants, 'release', 'org.jdeferred:jdeferred-android-aar:aar:1.2.3')
    }

    private static void checkJarDependency(
            @NonNull Collection<Variant> appVariants,
            @NonNull String variantName,
            @NonNull String aarCoodinate) {
        Variant appVariant = ModelHelper.getVariant(appVariants, variantName)

        AndroidArtifact appArtifact = appVariant.getMainArtifact()
        Dependencies artifactDependencies = appArtifact.getDependencies()

        Collection<AndroidLibrary> directLibraries = artifactDependencies.getLibraries()
        Assert.assertEquals(1, directLibraries.size())
        AndroidLibrary directLibrary = directLibraries.iterator().next()
        Assert.assertEquals(':library', directLibrary.getProject())

        List<? extends AndroidLibrary> transitiveLibraries = directLibrary.getLibraryDependencies()
        Assert.assertEquals(1, transitiveLibraries.size())
        AndroidLibrary transitiveLibrary = transitiveLibraries.get(0)
        Assert.assertEquals(variantName, aarCoodinate, transitiveLibrary.getResolvedCoordinates().toString())
    }
}
