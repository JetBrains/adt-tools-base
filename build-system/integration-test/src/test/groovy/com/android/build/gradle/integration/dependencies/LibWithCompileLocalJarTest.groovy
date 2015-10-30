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
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject
import com.android.builder.model.AndroidProject
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * test for compile local jar in libs
 */
@CompileStatic
class LibWithCompileLocalJarTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
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
    compile files('libs/util-1.0.jar')
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
    void "check compile local jar is packaged"() {
        // search in secondary jars only.
        assertThatAar(project.getAar("debug")).containsClass(
                "Lcom/example/android/multiproject/person/People;",
                AbstractAndroidSubject.ClassFileScope.SECONDARY)
    }

    @Test
    void "test library test contains local jar classes"() {
        project.execute("assembleDebugAndroidTest")

        assertThatApk(project.getTestApk("debug")).containsClass(
                "Lcom/example/android/multiproject/person/People;")
    }
}
