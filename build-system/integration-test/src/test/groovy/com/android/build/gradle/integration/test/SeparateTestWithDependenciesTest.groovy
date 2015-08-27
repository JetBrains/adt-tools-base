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

package com.android.build.gradle.integration.test
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Test separate test module that tests an application with some complicated dependencies :
 *  - the app imports a library importing a jar file itself.
 *  - use minification.
 */
@CompileStatic
public class SeparateTestWithDependenciesTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModuleWithDependencies")
            .create()

    @BeforeClass
    static void setup() {
        project.execute("clean", "assemble")
    }
    @Test
    void "check app contains all dependent classes"() {
        project.execute("clean", "assemble")
        File apk = project.getSubproject('app').getApk("debug")
        assertThatApk(apk).containsClass("Lcom/android/tests/jarDep/JarDependencyUtil;")
    }


    @Test
    void "check test app does not contain any minified application's dependent classes"() {
        project.execute("clean", "assemble")
        File apk = project.getSubproject('test').getApk("debug")
        assertThatApk(apk).doesNotContainClass("Lcom/android/tests/jarDep/JarDependencyUtil;")
    }
}
