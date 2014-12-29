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
import org.junit.AfterClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Integration tests for manifest merging.
 */
class ManifestMergingTest {

    @ClassRule
    static public GradleTestProject simpleManifestMergingTask = GradleTestProject.builder()
            .withName("simpleManifestMergingTask")
            .fromTestProject("simpleManifestMergingTask")
            .create()

    @ClassRule
    static public GradleTestProject libsTest = GradleTestProject.builder()
            .withName("libsTest")
            .fromSample("libsTest")
            .create()

    @ClassRule
    static public GradleTestProject flavors = GradleTestProject.builder()
            .withName("flavors")
            .fromSample("flavors")
            .create()


    @AfterClass
    static void cleanUp() {
        libsTest = null
        flavors = null
        simpleManifestMergingTask = null
    }

    @Test
    void "simple manifest merger"() {
        simpleManifestMergingTask.execute("clean", "manifestMerger")
    }

    @Test
    void "check manifest merging for libraries"() {
        libsTest.execute("clean", "build");
        File fileOutput = libsTest.
                file("libapp/build/" + FD_INTERMEDIATES + "/bundles/release/AndroidManifest.xml");

        assertTrue(fileOutput.exists());
    }

    @Test
    void "check manifest merger report"() {
        flavors.execute("clean", "assemble")

        File[] reports = flavors.getOutputFile("apk").listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.getName().startsWith("manifest-merger");
            }
        });
        assertEquals(8, reports.length);
    }

}
