/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.test.BaseTest;

import java.io.File;
import java.util.List;

/**
 * Base class for build tests.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
abstract class BuildTest extends BaseTest {

    protected File testDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testDir = getTestDir();
    }

    /**
     * Indicates whether the given Gradle version should be ignored in tests (for example, when a Gradle version has
     * not been publicly released yet.)
     *
     * @param gradleVersion the given Gradle version.
     * @return {@code true} if the given Gradle version should be ignored, {@code false} otherwise.
     */
    protected static boolean isIgnoredGradleVersion(String gradleVersion) {
        return !BasePlugin.GRADLE_TEST_VERSION.equals(gradleVersion);
    }

    protected File buildProject(
            @NonNull String testFolder,
            @NonNull String name,
            @NonNull String gradleVersion) {
        return runTasksOn(
                testFolder,
                name,
                gradleVersion,
                "clean", "assembleDebug", "lint");
    }

    protected File buildProject(
            @NonNull String testFolder,
            @NonNull String name,
            @NonNull String gradleVersion,
            @NonNull List<String> arguments) {
        return runTasksOn(
                testFolder,
                name,
                gradleVersion,
                arguments,
                "clean", "assembleDebug", "lint");
    }

}
