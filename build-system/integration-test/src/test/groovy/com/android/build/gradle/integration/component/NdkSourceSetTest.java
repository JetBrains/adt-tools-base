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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Tests features for jni source set.
 */
public class NdkSourceSetTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .useExperimentalGradleVersion(true)
            .create();

    @Before
    public void setUp() throws IOException {
        Files.append(
                "apply plugin: 'com.android.model.application'\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n",
                project.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void testIncludeSpecificFile() throws IOException {
        FileUtils.createFile(
                project.file("src/main/jni/uncompilable.c"),
                "Uncompilable source file.");
        Files.append(
                "model {\n"
                        + "    android {\n"
                        + "        sources {\n"
                        + "            main {\n"
                        + "                jni {\n"
                        + "                    source {\n"
                        + "                        include \"hello-jni.c\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                project.getBuildFile(),
                Charsets.UTF_8);
        project.execute("clean", "assembleDebug");
        Optional<File> obj =
                FileUtils.find(
                        project.file("build/intermediates/objectFiles/hello-jniX86DebugSharedLibraryMainC"),
                        "hello-jni.o");
        assertThat(obj.isPresent()).isTrue();
    }

    @Test
    public void testCFilter() throws IOException {
        FileUtils.createFile(
                project.file("src/main/jni/uncompilable.c"),
                "Uncompilable source file.");
        Files.move(
                project.file("src/main/jni/hello-jni.c"),
                project.file("src/main/jni/hello-jni.foo"));
        Files.append(
                "model {\n"
                        + "    android {\n"
                        + "        sources {\n"
                        + "            main {\n"
                        + "                jni {\n"
                        + "                    cFilter.setIncludes([\"**/*.foo\"])"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                project.getBuildFile(),
                Charsets.UTF_8);
        project.execute("clean", "assembleDebug");
        Optional<File> obj =
                FileUtils.find(
                        project.file("build/intermediates/objectFiles/hello-jniX86DebugSharedLibraryMainC"),
                        "hello-jni.o");
        assertThat(obj.isPresent()).isTrue();
    }
}
