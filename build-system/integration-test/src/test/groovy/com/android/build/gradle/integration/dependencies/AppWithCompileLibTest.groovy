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

package com.android.build.gradle.integration.dependencies
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.ApkHelper
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

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
/**
 * test for compile library in app
 */
@CompileStatic
class AppWithCompileLibTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create()
    static Map<String, AndroidProject> models

    @BeforeClass
    static void setUp() {
        project.getSubproject('app').getBuildFile() << """

dependencies {
    compile project(':library')
}
"""
        models = project.executeAndReturnMultiModel("clean", ":app:assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        models = null
    }

    @Test
    void "check compiled library is packaged"() {
        File apk = project.getSubproject('app').getApk("debug")

        assertTrue(ApkHelper.checkForClass(
                apk,
                "Lcom/example/android/multiproject/library/PersonView;"))
    }

    @Test
    void "check compiled library is in the model"() {
        Variant variant = ModelHelper.getVariant(models.get(':app').getVariants(), "debug")

        Dependencies deps = variant.getMainArtifact().getDependencies()
        Collection<AndroidLibrary> libraryDeps = deps.getLibraries()

        assertEquals("Check there is 1 dependency", 1, libraryDeps.size())
    }

    @Test
    void "check compiled library is in the android test dependency"() {
        // TODO
    }

    @Test
    void "check compiled library is in the unit test dependency"() {
        // TODO
    }
}
