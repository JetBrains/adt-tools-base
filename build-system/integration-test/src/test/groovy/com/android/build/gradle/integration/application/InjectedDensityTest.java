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
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Check that the built apk contains the correct resources.
 *
 * As specified by the build property injected by studio.
 */
public class InjectedDensityTest {

    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestApp(new EmptyAndroidTestApp("com.example.app.densities")).create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @BeforeClass
    public static void setup() throws IOException {

        String buildScript = GradleTestProject.getGradleBuildscript() + "\n"
                + "apply plugin: 'com.android.application'\n"
                + "android {\n"
                + "    compileSdkVersion rootProject.latestCompileSdk\n"
                + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 15"
                + "    }\n"
                + "    dependencies {\n"
                + "        compile 'com.android.support:appcompat-v7:21.0.3'\n"
                + "    }"
                + "}";

        Files.write(buildScript, sProject.getBuildFile(), Charsets.UTF_8);

    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void buildNormallyThenFiltered() throws IOException, ProcessException {
        sProject.execute("clean");
        checkFilteredBuild();
        checkFullBuild();
    }

    private void checkFullBuild() throws IOException, ProcessException {
        sProject.execute("assembleDebug");
        ApkSubject debug = expect.about(ApkSubject.FACTORY).that(sProject.getApk("debug"));
        debug.containsResource("drawable-xxxhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.containsResource("drawable-xxhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.containsResource("drawable-xhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.containsResource("drawable-hdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.containsResource("drawable-mdpi-v4/abc_ic_clear_mtrl_alpha.png");
    }

    private void checkFilteredBuild() throws IOException, ProcessException {
        sProject.execute(
                ImmutableList.of("-P" + AndroidProject.PROPERTY_BUILD_DENSITY + "=xxhdpi"),
                "assembleDebug");
        ApkSubject debug = expect.about(ApkSubject.FACTORY).that(sProject.getApk("debug"));
        debug.doesNotContainResource("drawable-xxxhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.containsResource("drawable-xxhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.doesNotContainResource("drawable-xhdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.doesNotContainResource("drawable-hdpi-v4/abc_ic_clear_mtrl_alpha.png");
        debug.doesNotContainResource("drawable-mdpi-v4/abc_ic_clear_mtrl_alpha.png");
    }
}
