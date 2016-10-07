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

package com.android.build.gradle.integration.performance;

import static com.android.build.gradle.integration.performance.BenchmarkMode.BUILD_FULL;
import static com.android.build.gradle.integration.performance.BenchmarkMode.BUILD_INC_JAVA;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Performance test for full and incremental build on ioschedule 2014
 */
@RunWith(Parameterized.class)
public class IOScheduleCodeChangeTest {

    @Parameterized.Parameters(name = "minify={0} jack={1}")
    public static Collection<Object[]> data() {
        // returns an array of boolean for all combinations of (proguard, jack).
        // Right now, only return the (false, false) and (false, true) cases.
        return Arrays.asList(new Object[][]{
//              {true, false},
//              {true, true},
                {false, false},
                {false, true},
        });
    }

    private final boolean jack;

    public IOScheduleCodeChangeTest(boolean proguard, boolean jack) {
        this.jack = jack;
        project = GradleTestProject.builder()
                .fromExternalProject("iosched")
                .withJack(jack)
                .withMinify(proguard)
                .create();
    }

    @Rule
    public GradleTestProject project;

    @Before
    public void setUp() throws IOException {
        if (jack) {
            TestFileUtils.searchAndReplace(
                    project.file("android/build.gradle"),
                    "buildToolsVersion \"21.1.2\"",
                    "buildToolsVersion '" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION + "'");
        }
        project.executeWithBenchmark("iosched2014", BUILD_FULL, "clean", "assembleDebug");
    }

    @Test
    public void incrementalBuildOnJavaChange() throws IOException {
        TestFileUtils.replaceLine(
                project.file("android/src/main/java/com/google/samples/apps"
                        + "/iosched/model/ScheduleItem.java"),
                30,
                "    public long startTime = 1;");
        project.executeWithBenchmark("iosched2014", BUILD_INC_JAVA, "assembleDebug");
    }
}
