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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenerateSourcesOnlyTest {


    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldLibraryApp())
                    .create();

    @Before
    public void createBuildFiles() throws IOException {
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(), "\n"
                + "apply plugin: \"com.android.application\"\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':lib')\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "     compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "     buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}");

        TestFileUtils.appendToFile(project.getSubproject("lib").getBuildFile(), "\n"
                + "apply plugin: \"com.android.library\"\n"
                + "\n"
                + "android {\n"
                + "     compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "     buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}");
    }

    @Test
    public void checkLibraryNotBuilt() {
        List<String> generateSources = getGenerateSourcesCommands();

        GradleBuildResult result = project.executor()
                .withArgument("-Pandroid.injected.generateSourcesOnly=true")
                .run(generateSources);

        assertThat(result.getStdout()).doesNotContain("compileDebugJava");
        assertThat(result.getStdout()).doesNotContain("compileReleaseJava");

        GradleBuildResult resultWithout = project.executor()
                .run(generateSources);

        assertThat(resultWithout.getStdout()).contains("compileReleaseJava");
    }


    private List<String> getGenerateSourcesCommands () {
        List<String> commands = Lists.newArrayList();
        for (Map.Entry<String, AndroidProject> entry: project.model().getMulti().entrySet()) {
            Variant debug = ModelHelper.getDebugVariant(entry.getValue());
            commands.add(entry.getKey() + ":" + debug.getMainArtifact().getSourceGenTaskName());
            for (AndroidArtifact artifact: debug.getExtraAndroidArtifacts()) {
                commands.add(entry.getKey() + ":" + artifact.getSourceGenTaskName());
            }
            for (JavaArtifact artifact: debug.getExtraJavaArtifacts()) {
                for (String taskName: artifact.getIdeSetupTaskNames()) {
                    commands.add(entry.getKey() + ":" + taskName);
                }
            }

        }
        return commands;
    }

}
