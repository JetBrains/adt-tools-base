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

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.utils.FileUtils
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Assemble tests for api.
 */
@CompileStatic
class ApiTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("api")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(FileUtils.sha1(project.file("app/build.gradle")))
                .isEqualTo("1059b5afd8437e4cfc8af9ba1fba76e48eef4a60")
        assertThat(FileUtils.sha1(project.file("lib/build.gradle")))
                .isEqualTo("00081a0d3932533e95e7811321b0e6c29c318caa")
    }
}
