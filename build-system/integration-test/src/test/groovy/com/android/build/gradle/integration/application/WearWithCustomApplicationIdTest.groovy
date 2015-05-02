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
import com.google.common.base.Throwables
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static junit.framework.Assert.fail

/**
 * Debug builds with a wearApp with applicationId that does not match that of the main application
 * should fail.
 */
@CompileStatic
class WearWithCustomApplicationIdTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("embedded")
            .create()

    @BeforeClass
    static void setUp() {
        def mainAppBuildGradle = project.file("main/build.gradle");

        mainAppBuildGradle.text = mainAppBuildGradle.text.replaceFirst(
                /flavor1 \{/,
                "flavor1 {\n" +
                        "        applicationId \"com.example.change.application.id.breaks.embed\"")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    public void "build should fail on applicationId mismatch"() {
        try {
            project.execute("clean", ":main:assembleFlavor1Release")
            fail("Build should fail: applicationId of wear app does not match the main application")
        } catch (BuildException e) {
            assert Throwables.getRootCause(e).message.contains(
                    "The main and the micro apps do not have the same package name");
        }
    }
}
