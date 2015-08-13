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
import com.android.builder.model.SyncIssue
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
/**
 * test for flavored dependency on a different package.
 */
@CompileStatic
class AppWithResolutionStrategyForJarTest {

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
      if (details.requested.name == 'guava') {
        details.useVersion '15.0'
      }
    }
  }
}

"""

        project.getSubproject('library').getBuildFile() << """

dependencies {
    compile 'com.google.guava:guava:17.0'
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

        checkJarDependency(appVariants, 'debug', 'com.google.guava:guava:jar:15.0')
        checkJarDependency(appVariants, 'release', 'com.google.guava:guava:jar:17.0')
    }

    private static void checkJarDependency(
            @NonNull Collection<Variant> appVariants,
            @NonNull String variantName,
            @NonNull String jarCoodinate) {
        Variant appVariant = ModelHelper.getVariant(appVariants, variantName)

        AndroidArtifact appArtifact = appVariant.getMainArtifact()
        Dependencies artifactDependencies = appArtifact.getDependencies()

        Collection<JavaLibrary> javaLibraries = artifactDependencies.getJavaLibraries()
        Assert.assertEquals(1, javaLibraries.size())
        JavaLibrary javaLibrary = javaLibraries.iterator().next()
        Assert.assertEquals(jarCoodinate, javaLibrary.getResolvedCoordinates().toString())
    }
}
