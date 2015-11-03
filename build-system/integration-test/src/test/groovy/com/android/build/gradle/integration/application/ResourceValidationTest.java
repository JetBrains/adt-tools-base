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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import java.io.File;

public class ResourceValidationTest {

    public static final AndroidTestApp TEST_APP = HelloWorldApp
            .forPlugin("com.android.application");

    static {
        TEST_APP.addFile(
                new TestSourceFile("src/main/res/drawable", "not_a_drawable.ext", "Content"));
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder().fromTestApp(TEST_APP)
            .captureStdErr(true).create();

    @Test
    public void checkResourceValidationCanBeDisabled() throws Exception {
        project.getStderr().reset();
        project.executeExpectingFailure("assembleDebug");

        assertThat(project.getStderr().toString()).contains(FileUtils.join("src", "main", "res",
                "drawable", "not_a_drawable.ext"));

        Files.append("\nproject.ext['android.disableResourceValidation'] = true",
                project.getBuildFile(),
                Charsets.UTF_8);

        project.execute("assembleDebug");

        File apk = project.getApk("debug");
        assertThatApk(apk).containsResource("drawable/not_a_drawable.ext");
    }
}
