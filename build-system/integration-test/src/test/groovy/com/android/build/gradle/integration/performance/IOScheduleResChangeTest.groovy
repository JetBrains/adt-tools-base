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

package com.android.build.gradle.integration.performance
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_FULL
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_INC_RES_ADD
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_INC_RES_EDIT

/**
 * Performance test for full and incremental build on ioschedule 2014
 */
@CompileStatic
class IOScheduleResChangeTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromExternalProject("iosched")
            .create()

    @Before
    public void setUp() {
        project.executeWithBenchmark("iosched2014", BUILD_FULL, "clean" , "assembleDebug")
    }

    @After
    void cleanUp() {
        project = null;
    }

    @Test
    void "Incremental Build on Resource Edit Change"() {
        project.replaceLine(
                "android/src/main/res/values/strings.xml",
                97,
                "    <string name=\"app_name\">Google I/O 2015</string>")

        project.executeWithBenchmark("iosched2014", BUILD_INC_RES_EDIT, "assembleDebug")
    }

    @Test
    void "Incremental Build on Resource Add Change"() {
        project.replaceLine(
                "android/src/main/res/values/strings.xml",
                97,
                "    <string name=\"app_name\">Google I/O 2015</string><string name=\"aaaa\">aaa</string>")

        project.executeWithBenchmark("iosched2014", BUILD_INC_RES_ADD, "assembleDebug")
    }
}
