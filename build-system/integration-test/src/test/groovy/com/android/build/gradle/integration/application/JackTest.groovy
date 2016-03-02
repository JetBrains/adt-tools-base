/*
 * Copyright (C) 2014 The Android Open Source Project
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



package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * Test Jack integration.
 */
@CompileStatic
@RunWith(Parameterized.class)
class JackTest {
    @Parameterized.Parameters(name = "jackInProcess={0}")
    public static Collection<Object[]> data() {
        return [
                [true] as Object[],
                [false] as Object[],
        ]
    }

    public boolean jackInProcess;

    public JackTest(boolean jackInProcess) {
        this.jackInProcess = jackInProcess
    }

    private final List<String> JACK_OPTIONS = ImmutableList.of(
            "-Pcom.android.build.gradle.integratonTest.useJack=true",
            "-Pcom.android.build.gradle.integratonTest.jackInProcess=" + jackInProcess,
            "-PCUSTOM_BUILDTOOLS=" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)

    @ClassRule
    static public GradleTestProject basic = GradleTestProject.builder()
            .withName("basic")
            .fromTestProject("basic")
            .create()

    @ClassRule
    static public GradleTestProject minify = GradleTestProject.builder()
            .withName("minify")
            .fromTestProject("minify")
            .create()

    @ClassRule
    static public GradleTestProject multiDex = GradleTestProject.builder()
            .withName("multiDex")
            .fromTestProject("multiDex")
            .create()

    @BeforeClass
    static void setUp() {
        AssumeUtil.assumeBuildToolsAtLeast(24, 0, 0)
    }

    @AfterClass
    static void cleanUp() {
        basic = null
        minify = null
        multiDex = null
    }

    @Test
    void assembleBasicDebug() {
        basic.execute(JACK_OPTIONS, "clean", "assembleDebug")
        assertThatApk(basic.getApk("debug")).contains("classes.dex");
        assertThat(basic.getStdout()).contains("JavaWithJack");
    }

    @Test
    void assembleMinifyDebug() {
        minify.execute(JACK_OPTIONS, "clean", "assembleDebug")
        assertThat(minify.getStdout()).contains("JavaWithJack");
    }

    @Test
    void assembleMultiDexDebug() {
        multiDex.execute(JACK_OPTIONS, "clean", "assembleDebug")
        assertThat(multiDex.getStdout()).contains("JavaWithJack");
    }

    @Test
    @Category(DeviceTests.class)
    void "basic connectedCheck"() {
        basic.executeConnectedCheck(JACK_OPTIONS)
    }

    @Test
    @Category(DeviceTests.class)
    void "multiDex connectedCheck"() {
        multiDex.executeConnectedCheck(JACK_OPTIONS)
    }

    @Test
    void "minify unitTests with Javac"() {
        minify.execute("testMinified")
    }

    @Test
    void "minify unitTests with Jack"() {
        minify.execute(JACK_OPTIONS, "clean", "testMinified")

        // Make sure javac was run.
        assertThat(minify.file("build/intermediates/classes/minified")).exists()

        // Make sure jack was not run.
        assertThat(minify.file("build/intermediates/jill")).doesNotExist()
    }
}
