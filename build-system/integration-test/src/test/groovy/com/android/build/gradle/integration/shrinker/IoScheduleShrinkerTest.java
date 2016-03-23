/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.shrinker.ShrinkerTestUtils.checkShrinkerWasUsed;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

/**
 * Simple test to check that built-in shrinker doesn't crash on iosched.
 */
public class IoScheduleShrinkerTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromExternalProject("iosched")
            .create();

    @Before
    public void skipOnJack() throws Exception {
        Assume.assumeFalse(GradleTestProject.USE_JACK);
    }

    @Before
    public void enableShrinker() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":android").getBuildFile(),
                "android.buildTypes.release.useProguard = false");
    }

    @Test
    public void shrinkerDoesntFail() throws Exception {
        project.execute(":android:assembleRelease");

        GradleTestProject androidSubproject = project.getSubproject(":android");
        File releaseApk = androidSubproject.getApk("release", "unsigned");

        checkShrinkerWasUsed(androidSubproject);
        assertThatApk(releaseApk)
                .containsClass("Lcom/google/samples/apps/iosched/ui/SettingsActivity;");
        // Check unused parts of Guava were removed.
        assertThatApk(releaseApk)
                .doesNotContainClass("Lcom/google/common/annotations/GwtCompatible;");
    }
}
