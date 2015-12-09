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

package com.android.build.gradle.integration.testing
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
/**
 * Runs tests in a big, complicated project.
 */
@CompileStatic
class UnitTestingComplexProjectTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("unitTestingComplexProject")
            .create()

    @Test
    public void appProject() throws Exception {
        project.execute("clean", "test")
    }

    @Test
    public void libProject() throws Exception {
        // Make the top-level project a library. Libraries depending on libraries are an edge case
        // when it comes to generating and using R classes.
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").buildFile,
                "com.android.application",
                "com.android.library")
        project.execute("clean", "test")
    }
}
