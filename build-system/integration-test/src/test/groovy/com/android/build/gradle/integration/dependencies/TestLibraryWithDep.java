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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.ide.common.process.ProcessException;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

public class TestLibraryWithDep {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("libTestDep")
            .create();

    @BeforeClass
    public static void setUp() {
        project.executeAndReturnMultiModel("clean", "assembleDebugAndroidTest");
    }

    @Test
    public void checkLibDependencyJarIsPackaged() throws IOException, ProcessException {
        assertThatApk(project.getTestApk("debug"))
                .containsClass("Lcom/google/common/base/Splitter;");
    }
}

