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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;

import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the sourceSets task.
 */
public class SourceSetsTaskTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void runsSuccessfully() throws Exception {
        project.execute("sourceSets");

        assertThat(project.getStdout()).contains(
                "debug\n"
                + "-----\n"
                + "Compile configuration: debugCompile\n"
                + "build.gradle name: android.sourceSets.debug\n"
                + "Java sources: [src/debug/java]\n"
                + "Manifest file: src/debug/AndroidManifest.xml\n"
                + "Android resources: [src/debug/res]\n"
                + "Assets: [src/debug/assets]\n"
                + "AIDL sources: [src/debug/aidl]\n"
                + "RenderScript sources: [src/debug/rs]\n"
                + "JNI sources: [src/debug/jni]\n"
                + "JNI libraries: [src/debug/jniLibs]\n"
                + "Java-style resources: [src/debug/resources]\n"
                + "\n");
    }
}
