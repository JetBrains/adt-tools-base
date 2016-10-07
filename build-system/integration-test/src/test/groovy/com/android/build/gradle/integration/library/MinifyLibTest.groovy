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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.shrinker.ShrinkerTestUtils
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.nio.charset.Charset

/**
 * Assemble tests for minifyLib.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class MinifyLibTest {
    @Parameterized.Parameters(name = "useProguard = {0}")
    public static Collection<Object[]> data() {
        return [
                [true] as Object[],
                [false] as Object[],
        ]
    }

    @Parameterized.Parameter(0)
    public boolean useProguard

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minifyLib")
            .create()

    @Test
    public void consumerProguardFile() throws Exception {
        if (!useProguard) {
            ShrinkerTestUtils.enableShrinker(project.getSubproject(":app"), "debug")
        }

        project.execute(":app:assembleDebug")
        File apk = project.getSubproject(":app").getApk("debug")
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/StringProvider;")
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/UnusedClass;")
    }

    @Test
    public void shrinkingTheLibrary() throws Exception {
        enableLibShrinking()

        project.execute(":app:assembleDebug")

        File aar = project.getSubproject(":lib").getAar("release")
        TruthHelper.assertThatAar(aar).containsClass("Lcom/android/tests/basic/StringProvider;")
        TruthHelper.assertThatAar(aar).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;")

        File apk = project.getSubproject(":app").getApk("debug")
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/StringProvider;")
        TruthHelper.assertThatApk(apk).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;")
    }

    /**
     * Tests the edge case of a library with no classes (after shrinking). We should at least not
     * crash.
     */
    @Test
    public void shrinkingTheLibrary_noClasses() throws Exception {
        enableLibShrinking()
        Files.write(
                "", // Remove the -keep rules.
                project.getSubproject(":lib").file("config.pro"),
                Charset.defaultCharset());

        project.execute(":lib:assembleDebug")
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }

    private void enableLibShrinking() {
        project.getSubproject(":lib").buildFile << """android {
            buildTypes.release {
                minifyEnabled true
                proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'
            }
        }
        """

        if (!useProguard) {
            ShrinkerTestUtils.enableShrinker(project.getSubproject(":lib"), "release")
        }
    }

}
