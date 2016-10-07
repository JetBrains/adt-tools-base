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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope.ALL;
import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope.MAIN;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.internal.aapt.Aapt;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.JavaVersion;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Assemble tests for multiDex. */
@RunWith(FilterableParameterized.class)
public class MultiDexTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Rule public Adb adb = new Adb();

    @Parameterized.Parameters(name = "dexInProcess = {0}")
    public static List<Boolean> data() {
        if (GradleTestProject.USE_JACK) {
            return Lists.newArrayList(true);
        } else {
            return Lists.newArrayList(true, false);
        }
    }

    @Parameterized.Parameter public boolean dexInProcess;

    @Before
    public void disableDexInProcess() throws IOException {
        if (!dexInProcess) {
            DexInProcessHelper.disableDexInProcess(project.getBuildFile());
        }
    }

    @Test
    public void checkNormalBuild() throws Exception {
        checkNormalBuild(true);
    }

    @Test
    public void checkBuildWithoutKeepRuntimeAnnotatedClasses() throws Exception {
        checkNormalBuild(true);
    }

    private void checkNormalBuild(boolean keepRuntimeAnnotatedClasses) throws Exception {

        if (!keepRuntimeAnnotatedClasses) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");
        }

        project.execute("assembleDebug", "assembleAndroidTest");

        // additional classes that will be found in the list, if build tools version
        // is less than Aapt.VERSION_FOR_MAIN_DEX_LIST
        List<String> nonMandatoryMainDexClasses =
                Lists.newArrayList(
                        "com/android/tests/basic/Used",
                        "com/android/tests/basic/DeadCode",
                        "com/android/tests/basic/Main",
                        "com/android/tests/basic/OtherActivity");

        if (JavaVersion.current().isJava8Compatible()) {
            // javac 1.8 puts the InnerClasses attribute from R to R$id inside classes that use
            // R$id, like Main. The main dex list builder picks it up from the constant pool.
            nonMandatoryMainDexClasses.addAll(
                    ImmutableList.of(
                            "com/android/tests/basic/R",
                            "com/android/tests/basic/R$id",
                            "com/android/tests/basic/R$layout"));
        }

        List<String> mandatoryClasses =
                Lists.newArrayList("android/support/multidex/MultiDexApplication",
                        "com/android/tests/basic/MyAnnotation");
        if (keepRuntimeAnnotatedClasses) {
            mandatoryClasses.add("com/android/tests/basic/ClassWithRuntimeAnnotation");
        }

        assertMainDexListContains(
                "debug",
                mandatoryClasses,
                nonMandatoryMainDexClasses);

        String transform = GradleTestProject.USE_JACK ? "jack" : "dex";

        // The path is fragile, and depends on internals of dex transforms. If this changes
        // in the production code, it should be updated here.
        File dexDir =
                FileUtils.join(
                        project.getIntermediatesDir(),
                        "transforms",
                        transform,
                        "ics",
                        "debug",
                        "folders",
                        "1000",
                        "1f",
                        "main");
        // manually inspect the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        File classesDex = FileUtils.join(dexDir, "classes.dex");

        assertThatZip(project.getApk("ics", "debug"))
                .containsFileWithContent("classes.dex", Files.toByteArray(classesDex));

        File classes2Dex = FileUtils.join(dexDir, "classes2.dex");

        assertThatZip(project.getApk("ics", "debug"))
                .containsFileWithContent("classes2.dex", Files.toByteArray(classes2Dex));

        commonApkChecks("debug");

        assertThatApk(project.getTestApk("ics", "debug"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");
        assertThatApk(project.getTestApk("lollipop", "debug"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        // Both test APKs should contain a class from Junit.
        assertThatApk(project.getTestApk("ics", "debug")).containsClass("Lorg/junit/Assert;");
        assertThatApk(project.getTestApk("lollipop", "debug")).containsClass("Lorg/junit/Assert;");

        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;");
        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkMinifiedBuild() throws Exception {
        project.execute("assembleMinified");

        assertMainDexListContains(
                "minified",
                ImmutableList.of("android/support/multidex/MultiDexApplication"),
                ImmutableList.of(
                        "com/android/tests/basic/Used",
                        "com/android/tests/basic/Main",
                        "com/android/tests/basic/OtherActivity"));

        commonApkChecks("minified");

        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;");
        assertThatApk(project.getApk("ics", "minified"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkAdditionalParameters() throws Exception {
        Assume.assumeFalse(
                "additionalParameters not supported by Jack", GradleTestProject.USE_JACK);

        FileUtils.deletePath(
                FileUtils.join(
                        project.getTestDir(),
                        "src",
                        "main",
                        "java",
                        "com",
                        "android",
                        "tests",
                        "basic",
                        "manymethods"));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.dexOptions.additionalParameters = ['--minimal-main-dex']\n");

        project.execute("assembleIcsDebug", "assembleIcsDebugAndroidTest");

        assertThatApk(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;", ALL);
        assertThatApk(project.getApk("ics", "debug"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;", MAIN);

        // Make sure --minimal-main-dex was not used for the test APK.
        assertThatApk(project.getTestApk("ics", "debug")).contains("classes.dex");
        assertThatApk(project.getTestApk("ics", "debug")).doesNotContain("classes2.dex");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.dexOptions.additionalParameters '--set-max-idx-number=10'\n");

        GradleBuildResult result = project.executor().expectFailure().run("assembleIcsDebug");

        assertThat(result.getStderr()).contains("main dex capacity exceeded");
    }

    @Test
    public void checkManifestKeepListFilter() throws Exception {
        Assume.assumeFalse(aaptSupportsMultiDexList());
        project.execute("collectIcsDebugMultiDexComponents");
        File manifestKeep =
                FileUtils.join(
                        project.getIntermediatesDir(),
                        "multi-dex",
                        "ics",
                        "debug",
                        "manifest_keep.txt");
        assertThat(Files.toString(manifestKeep, Charsets.UTF_8))
                .contains("com.android.tests.basic.Main");
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "afterEvaluate {\n"
                        + "    project.collectIcsDebugMultiDexComponents.filter({\n"
                        + "        name, attrs -> \n"
                        + "            !name.equals(\"activity\") ||\n"
                        + "            !\"com.android.tests.basic.Main\".equals(attrs.get(\"android:name\")); })\n"
                        + "}\n");

        project.execute("collectIcsDebugMultiDexComponents");

        assertThat(Files.toString(manifestKeep, Charsets.UTF_8))
                .doesNotContain("com.android.tests.basic.Main");
    }

    private void commonApkChecks(String buildType) throws Exception {
        assertThatApk(project.getApk("ics", buildType))
                .containsClass("Landroid/support/multidex/MultiDexApplication;");
        assertThatApk(project.getApk("lollipop", buildType))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        for (String flavor : ImmutableList.of("ics", "lollipop")) {
            assertThatApk(project.getApk(flavor, buildType))
                    .containsClass("Lcom/android/tests/basic/Main;");
            assertThatApk(project.getApk(flavor, buildType))
                    .containsClass("Lcom/android/tests/basic/Used;");
            assertThatApk(project.getApk(flavor, buildType))
                    .containsClass("Lcom/android/tests/basic/Kept;");
        }
    }

    private void assertMainDexListContains(
            @NonNull String buildType,
            @NonNull List<String> mandatoryClasses,
            @NonNull List<String> permittedToBeInMainDexClasses)
            throws Exception {
        // Jack done not produce maindexlist.txt
        if (GradleTestProject.USE_JACK) {
            return;
        }
        File listFile =
                FileUtils.join(
                        project.getIntermediatesDir(),
                        "multi-dex",
                        "ics",
                        buildType,
                        "maindexlist.txt");

        Set<String> lines = Files.readLines(listFile, Charsets.UTF_8)
                .stream()
                .filter(line -> !line.isEmpty())
                .map(line -> line.replace(".class", ""))
                .collect(Collectors.toSet());

        // MultiDexApplication needs to be there
        assertThat(lines).containsAllIn(mandatoryClasses);

        // it may contain only classes from the support library
        // Check that the main dex list only contains:
        //  - The multidex support libray
        //  - The mandatory classes
        //  - The permittedToBeInMainDex classes.
        Set<String> unwantedExtraClasses =
                lines.stream()
                        .filter(line -> !line.startsWith("android/support/multidex"))
                        .collect(Collectors.toSet());
        unwantedExtraClasses.removeAll(mandatoryClasses);
        unwantedExtraClasses.removeAll(permittedToBeInMainDexClasses);

        assertThat(unwantedExtraClasses).named("Unwanted classes in main dex").isEmpty();

    }

    private static boolean aaptSupportsMultiDexList() {
        return Revision.parseRevision(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)
                        .compareTo(Aapt.VERSION_FOR_MAIN_DEX_LIST)
                >= 0;
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException {
        project.execute(
                "assembleIcsDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebug",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        project.execute("connectedCheck");
    }
}
