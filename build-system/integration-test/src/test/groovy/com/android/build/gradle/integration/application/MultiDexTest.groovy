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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
/**
 * Assemble tests for multiDex.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class MultiDexTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiDex")
            .withHeap("2048M")
            .create()

    @Parameterized.Parameters(name = "dexInProcess = {0}")
    public static Collection<Object[]> data() {
        return [
                [true] as Object[],
                [false] as Object[],
        ]
    }

    @Parameterized.Parameter(0)
    public boolean dexInProcess

    @Before
    public void setDexInProcess() {
        project.buildFile << "android.dexOptions.dexInProcess = " + dexInProcess
    }

    @Test
    void "check normal build"() {
        project.execute("assembleDebug", "assembleAndroidTest")

        assertMainDexListContainsExactly("debug",
                "com/android/tests/basic/Used",
                "com/android/tests/basic/DeadCode",
                "com/android/tests/basic/Main")

        // manually inspect the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        assertThatZip(project.getApk("ics", "debug")).containsFileWithContent(
                "classes.dex",
                Files.toByteArray(project.file(
                        "build/" + FD_INTERMEDIATES +
                                "/transforms/dex/" +
                                "ics/debug/" +
                                "folders/1000/1f/main/" +
                                "classes.dex")))

        assertThatZip(project.getApk("ics", "debug")).containsFileWithContent(
                "classes2.dex",
                Files.toByteArray(project.file(
                        "build/" + FD_INTERMEDIATES +
                        "/transforms/dex/" +
                        "ics/debug/" +
                        "folders/1000/1f/main/" +
                        "classes2.dex")))

        commonApkChecks("debug")

        assertThatApk(project.getTestApk("ics", "debug")).
                doesNotContainClass("Landroid/support/multidex/MultiDexApplication;")
        assertThatApk(project.getTestApk("lollipop", "debug")).
                doesNotContainClass("Landroid/support/multidex/MultiDexApplication;")

        // Both test APKs should contain a class from Junit.
        assertThatApk(project.getTestApk("ics", "debug")).containsClass("Lorg/junit/Assert;")
        assertThatApk(project.getTestApk("lollipop", "debug")).containsClass("Lorg/junit/Assert;")

        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;")
        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/DeadCode;")
    }

    @Test
    void "check minified build"() {
        project.execute("assembleMinified")

        assertMainDexListContainsExactly("minified",
                "com/android/tests/basic/Used",
                "com/android/tests/basic/Main")

        commonApkChecks("minified")

        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;")
        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;")
    }

    private void commonApkChecks(String buildType) {
        assertThatApk(project.getApk("ics", buildType)).
                containsClass("Landroid/support/multidex/MultiDexApplication;")
        assertThatApk(project.getApk("lollipop", buildType)).
                doesNotContainClass("Landroid/support/multidex/MultiDexApplication;")

        assertThatApk(project.getApk("ics", buildType))
                .containsClass("Lcom/android/tests/basic/Main;")
        assertThatApk(project.getApk("ics", buildType))
                .containsClass("Lcom/android/tests/basic/Used;")
        assertThatApk(project.getApk("ics", buildType))
                .containsClass("Lcom/android/tests/basic/Kept;")
    }

    private assertMainDexListContainsExactly(String buildType, String... expected) {
        File listFile = project.file("build/intermediates/multi-dex/ics/${buildType}/mainDexList.txt")
        Iterable<String> actual = listFile
                .readLines()
                .findAll{!it.isEmpty() && !it.startsWith("android/support/multidex")}
                .collect{it - ~/.class$/}

        assertThat(actual).containsExactlyElementsIn(expected.toList())
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
