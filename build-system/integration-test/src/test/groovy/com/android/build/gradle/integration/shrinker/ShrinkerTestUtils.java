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

package com.android.build.gradle.integration.shrinker;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import java.io.IOException;

/**
 * Utility code for testing the new built-in class shrinker.
 */
public class ShrinkerTestUtils {
    public static void checkShrinkerWasUsed(GradleTestProject project) {
        // Sanity check, to make sure we're testing the right thing.
        assertThat(project.file("build/intermediates/transforms/newClassShrinker")).exists();
    }

    public static void enableShrinker(GradleTestProject project, String buildType)
            throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n\nandroid.buildTypes." + buildType + ".useProguard = false");
    }
}
