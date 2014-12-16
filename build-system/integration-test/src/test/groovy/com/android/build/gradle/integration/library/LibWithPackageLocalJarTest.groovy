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
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
/**
 * test for package (publish) local jar in libs
 */
@CompileStatic
class LibWithPackageLocalJarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithProvidedLocalJar")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.library'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    publish files('libs/util-1.0.jar')
}
"""

        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void "check packaged local jar is packaged"() {
        ZipFile aar = new ZipFile(project.getAar("debug"))
        assertNotNull(aar.getEntry("libs/util-1.0.jar"))
    }

    @Test
    void "check packaged local jar is not in the model"() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug")

        Dependencies deps = variant.getMainArtifact().getDependencies()
        Collection<JavaLibrary> javaLibs = deps.getJavaLibraries()

        assertTrue("Check there is no dependency", javaLibs.isEmpty())
    }

    @Test
    void "check packaged local jar is not in the android test dependency"() {
    }

    @Test
    void "check packaged local jar is not in the unit test dependency"() {
    }
}
