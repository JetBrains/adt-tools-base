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

package com.android.build.gradle.integration.shrinker

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.shrinker.ShrinkerTestUtils.checkShrinkerWasUsed
/**
 * Tests based on the "minify" test project, which contains unused classes, reflection and
 * JaCoCo classes.
 */
@CompileStatic
class MinifyProjectShrinkerTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minify")
            .create()

    @Before
    public void skipOnJack() throws Exception {
        Assume.assumeFalse(GradleTestProject.USE_JACK)
    }

    @Before
    public void enableShrinker() throws Exception {
        project.buildFile << """
            android {
                buildTypes.minified {
                    useProguard false
                }

                testBuildType = "minified"
            }
        """
    }

    @Test
    public void "APK is correct"() throws Exception {
        project.execute("assembleMinified")
        checkShrinkerWasUsed(project)
        assertThatApk(project.getApk("minified")).containsClass("Lcom/android/tests/basic/Main;")
        assertThatApk(project.getApk("minified")).containsClass("Lcom/android/tests/basic/StringProvider;")
        assertThatApk(project.getApk("minified")).containsClass("Lcom/android/tests/basic/IndirectlyReferencedClass;")
        assertThatApk(project.getApk("minified")).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;")
    }
}
