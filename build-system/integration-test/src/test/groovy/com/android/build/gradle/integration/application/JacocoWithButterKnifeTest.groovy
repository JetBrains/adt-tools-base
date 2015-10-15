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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Check Jacoco doesn't get broken with annotation processor that dumps .java files in the
 * compiler out folder.
 */
@CompileStatic
class JacocoWithButterKnifeTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("jacocoWithButterKnife")
            .withoutNdk()
            .create()

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void build() {
        project.execute("transformClassesWithJacocoForDebug")

        File javaFile = FileUtils.join(project.getTestDir(),
                "build",
                "intermediates",
                "classes",
                "debug",
                "com",
                "test",
                "jacoco",
                "annotation",
                "BindActivity\$\$ViewBinder.java")
        assertThat(javaFile).exists();
    }
}
