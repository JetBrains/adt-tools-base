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
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.DexInProcessHelper
import com.android.utils.FileUtils
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope.ALL
import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope.MAIN
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip
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

    @Rule
    public Adb adb = new Adb();

    @Parameterized.Parameters(name = "dexInProcess = {0}")
    public static Collection<Object[]> data() {
        if (GradleTestProject.USE_JACK) {
            return [
                    [false] as Object[],
            ]
        } else {
            return [
                    [true] as Object[],
                    [false] as Object[],
            ]
        }
    }

    @Parameterized.Parameter(0)
    public boolean dexInProcess

    @Before
    public void setDexInProcess() {
        if (dexInProcess) {
            DexInProcessHelper.enableDexInProcess(project.buildFile)
        }
    }

    @Test
    void "check normal build"() {
        project.execute("assembleDebug", "assembleAndroidTest")

        def expected = [
                "com/android/tests/basic/Used",
                "com/android/tests/basic/DeadCode",
                "com/android/tests/basic/Main"
        ]

        if (JavaVersion.current().isJava8Compatible()) {
            // javac 1.8 puts the InnerClasses attribute from R to R$id inside classes that use
            // R$id, like Main. The main dex list builder picks it up from the constant pool.
            expected += [
                    'com/android/tests/basic/R',
                    'com/android/tests/basic/R$id',
                    'com/android/tests/basic/R$layout',
            ]
        }

        assertMainDexListContains("debug", expected)

        String transform = GradleTestProject.USE_JACK ? "jack" : "dex"

        // manually inspect the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        File classesDex =
                project.file("build/intermediates/transforms/$transform/ics/debug/" +
                        "folders/1000/1f/main/classes.dex")

        assertThatZip(project.getApk("ics", "debug")).containsFileWithContent(
                "classes.dex",
                Files.toByteArray(classesDex))

        File classes2Dex =
                project.file("build/intermediates/transforms/$transform/ics/debug/" +
                        "folders/1000/1f/main/classes2.dex")

        assertThatZip(project.getApk("ics", "debug")).containsFileWithContent(
                "classes2.dex",
                Files.toByteArray(classes2Dex))

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

        assertMainDexListContains(
                "minified",
                [ "com/android/tests/basic/Used", "com/android/tests/basic/Main"])

        commonApkChecks("minified")

        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;")
        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;")
    }

    @Test
    public void "check additional flags"() throws Exception {
        Assume.assumeFalse("additionalParameters not supported by Jack", GradleTestProject.USE_JACK)

        FileUtils.deletePath(project.file("src/main/java/com/android/tests/basic/manymethods"))

        project.buildFile << "\nandroid.dexOptions.additionalParameters = ['--minimal-main-dex']\n"

        project.execute("assembleIcsDebug", "assembleIcsDebugAndroidTest")

        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;", ALL)
        assertThatApk(project.getApk("ics", "debug"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;", MAIN)

        // Make sure --minimal-main-dex was not used for the test APK.
        assertThatApk(project.getTestApk("ics", "debug")).contains("classes.dex")
        assertThatApk(project.getTestApk("ics", "debug")).doesNotContain("classes2.dex")

        project.buildFile << "\nandroid.dexOptions.additionalParameters '--set-max-idx-number=10'\n"

        project.executeExpectingFailure("assembleIcsDebug")

        assertThat(project.stderr).contains("main dex capacity exceeded")
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

    private assertMainDexListContains(String buildType, List<String> expected) {
        // Jack do not produce maindexlist.txt
        if (GradleTestProject.USE_JACK) {
            return
        }
        File listFile = project.file("build/intermediates/multi-dex/ics/${buildType}/maindexlist.txt")
        Iterable<String> actual = listFile
                .readLines()
                .findAll{!it.isEmpty() && !it.startsWith("android/support/multidex")}
                .collect{it - ~/.class$/}

        assertThat(actual).containsExactlyElementsIn(expected.toList())
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.execute(
                "assembleIcsDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebug",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        project.execute("connectedCheck");
    }
}
