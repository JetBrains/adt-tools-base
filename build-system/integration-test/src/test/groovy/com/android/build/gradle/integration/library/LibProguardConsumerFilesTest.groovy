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
import com.android.build.gradle.integration.common.utils.FileHelper
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

/**
 * Assemble tests for libProguarConsumerFiles.
 */
class LibProguardConsumerFilesTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("libProguardConsumerFiles")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "build")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check proguard.txt has been correctly merged"() {
        File debugFileOutput = project.file("build/" + FD_INTERMEDIATES + "/bundles/debug/proguard.txt")
        File releaseFileOutput = project.file("build/" + FD_INTERMEDIATES + "/bundles/release/proguard.txt")

        FileHelper.checkContent(debugFileOutput, "A")
        FileHelper.checkContent(releaseFileOutput, ["A", "B", "C"])
    }
}
