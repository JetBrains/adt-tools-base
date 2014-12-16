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
import com.android.build.gradle.integration.common.utils.ApkHelper
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

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

/**
 * test for provided local jar in app
 */
@CompileStatic
class AppWithProvidedLocalJarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithProvidedLocalJar")
            .create()
    static AndroidProject model

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    provided files('libs/util-1.0.jar')
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
    void "check provided local jar is not packaged"() {
        File apk = project.getApk("debug")

        assertFalse(ApkHelper.checkForClass(
                apk,
                "Lcom/example/android/multiproject/person/People;"))
    }

    @Test
    void "check provided local jar is in the main artifact dependency"() {
        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug")

        Dependencies deps = variant.getMainArtifact().getDependencies()
        Collection<JavaLibrary> javaLibs = deps.getJavaLibraries()

        assertEquals("Check there is 1 dependency", 1, javaLibs.size())
    }

    @Test
    void "check provided local jar is in the android test dependency"() {
        // TODO
    }

    @Test
    void "check provided local jar is in the unit test dependency"() {
        // TODO
    }
}
