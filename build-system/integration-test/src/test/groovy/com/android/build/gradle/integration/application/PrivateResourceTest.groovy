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
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip

/**
 * Assemble tests for privateResources.
 */
class PrivateResourceTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("privateResources")
            .create()

    @BeforeClass
    static void setup() {
        project.execute("clean", "assemble");
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check private resources resources"() {
        String expected = """\
string mylib_app_name
string mylib_public_string
"""
        assertThatZip(project.getSubproject('mylibrary').getAar("release")).containsFileWithContent('public.txt', expected);
        assertThatZip(project.getSubproject('mylibrary').getAar("debug")).containsFileWithContent('public.txt', expected);

        // No public resources: file should exist but be empty
        assertThatZip(project.getSubproject('mylibrary2').getAar("debug")).containsFileWithContent('public.txt', "");
        assertThatZip(project.getSubproject('mylibrary2').getAar("release")).containsFileWithContent('public.txt', "");
    }
}
