/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.BuildScriptGenerator;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.gradle.api.JavaVersion;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Test Jack integration.
 */
@RunWith(FilterableParameterized.class)
public class JackTest {
    @Parameterized.Parameters(name = "jackInProcess={0}, forComponentPlugin={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // jackInProcess forComponentPlugin
                {true,           false},
                {false,          false},
                {true,           true},
                {false,          true},
        });
    }

    private boolean jackInProcess;
    private boolean forComponentPlugin;

    @Rule
    public GradleTestProject project;

    public JackTest(boolean jackInProcess, boolean forComponentPlugin) {
        this.jackInProcess = jackInProcess;
        this.forComponentPlugin = forComponentPlugin;

        project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .useExperimentalGradleVersion(forComponentPlugin)
            .create();
    }

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue("Jack tool requires Java 7", JavaVersion.current().isJava7Compatible());
        String buildScript = new BuildScriptGenerator(
                "apply plugin: '${application_plugin}'\n"
                        + "${model_start}"
                        + "android {\n"
                        + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "    buildToolsVersion '" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION + "'\n"
                        + "    defaultConfig {\n"
                        + "        jackOptions {\n"
                        + "            enabled true\n"
                        + "            jackInProcess " + jackInProcess + "\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            testCoverageEnabled false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "${model_end}").build(forComponentPlugin);
        Files.append(
                buildScript,
                project.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void assembleDebug() throws IOException {
        project.execute("clean", "assembleDebug");
        assertThatApk(project.getApk("debug")).contains("classes.dex");
        assertThat(project.getStdout()).contains("transformJackWithJack");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }

    @Test
    public void unitTest() {
        project.execute("testDebug");

        // Make sure javac was run.
        assertThat(project.file("build/intermediates/classes/debug")).exists();

        // Make sure jack was not run.
        assertThat(project.file("build/intermediates/jill")).doesNotExist();
    }
}
