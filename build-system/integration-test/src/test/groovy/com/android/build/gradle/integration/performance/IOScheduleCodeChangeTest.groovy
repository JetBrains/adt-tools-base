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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_FULL
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.BenchmarkMode.BUILD_INC_JAVA
/**
 * Performance test for full and incremental build on ioschedule 2014
 */
@RunWith(Parameterized.class)
@CompileStatic
class IOScheduleCodeChangeTest {

    @Parameterized.Parameters(name="minify={0} jack={1}")
    public static Collection<Object[]> data() {
        // returns an array of boolean for all combinations of (proguard, jack).
        // Right now, only return the (false, false) and (false, true) cases.
        return [
//                [true, false].toArray(),
//                [true, true].toArray(),
                [false, false].toArray(),
                [false, true].toArray(),
        ];
    }

    private final boolean proguard
    private final boolean jack

    IOScheduleCodeChangeTest(boolean proguard, boolean jack) {
        this.proguard = proguard
        this.jack = jack
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromExternalProject("iosched")
            .withJack(jack)
            .withMinify(proguard)
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
    void "Incremental Build on Java Change"() {
        project.replaceLine(
                "android/src/main/java/com/google/samples/apps/iosched/model/ScheduleItem.java",
                30,
                "    public long startTime = 1;")
        project.executeWithBenchmark("iosched2014", BUILD_INC_JAVA, "assembleDebug")
    }
}
