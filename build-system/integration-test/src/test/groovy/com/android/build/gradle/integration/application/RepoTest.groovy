/*
 * Copyright (C) 2014 The Android Open Source Project;
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import org.junit.ClassRule
import org.junit.Test

/**
 * Integration test for uploadAchives with multiple projects.
 */
class RepoTest {

    @ClassRule
    static public GradleTestProject app = GradleTestProject.builder()
            .withName("app")
            .fromTestProject("repo/app")
            .create()

    @ClassRule
    static public GradleTestProject baseLibrary = GradleTestProject.builder()
            .withName("baseLibrary")
            .fromTestProject("repo/baseLibrary")
            .create()

    @ClassRule
    static public GradleTestProject library = GradleTestProject.builder()
            .withName("library")
            .fromTestProject("repo/library")
            .create()

    @ClassRule
    static public GradleTestProject util = GradleTestProject.builder()
            .withName("util")
            .fromTestProject("repo/util")
            .create()

    @Test
    void repo() {
        try {
            util.execute("clean", "uploadArchives")
            baseLibrary.execute("clean", "uploadArchives")
            library.execute("clean", "uploadArchives")
            app.execute("clean", "uploadArchives")
        } finally {
            // clean up the test repository.
            File testRepo = new File(app.testDir, "../testrepo")
            testRepo.delete()
        }
    }
}
