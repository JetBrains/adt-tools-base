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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class InstantRunChangeDeviceTest {

    private static final Set<BuildTarget> buildTargetsToTest = EnumSet.allOf(BuildTarget.class);

    @Parameterized.Parameters(name = "from {0} to {1}, {2}")
    public static Collection<Object[]> scenarios() {
        // Test all change combinations (plus packaging modes).
        // We want (BuildTarget x BuildTarget) \ id(BuildTarget)
        return Sets.cartesianProduct(
                        ImmutableList.of(
                                buildTargetsToTest,
                                buildTargetsToTest,
                                EnumSet.allOf(Packaging.class)))
                .stream()
                .filter(fromTo -> fromTo.get(0) != fromTo.get(1))
                .map(fromTo -> Iterables.toArray(fromTo, Object.class))
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter(0)
    public BuildTarget firstBuild;

    @Parameterized.Parameter(1)
    public BuildTarget secondBuild;

    @Parameterized.Parameter(2)
    public Packaging packaging;

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();
    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @NonNull
    private static File getMainApk(@NonNull List<InstantRunArtifact> artifacts) {
        InstantRunArtifact apk = Iterables.getOnlyElement(artifacts);
        assertThat(apk.type).named("Main apk type").isEqualTo(InstantRunArtifactType.MAIN);
        return apk.file;
    }

    @Test
    public void switchScenario() throws Exception {
        AndroidProject model = mProject.model().getSingle();
        File apk = model.getVariants().stream()
                .filter(variant -> variant.getName().equals("debug")).iterator().next()
                .getMainArtifact()
                .getOutputs().iterator().next()
                .getOutputs().iterator().next()
                .getOutputFile();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        String startBuildId;
        mProject.execute("clean");

        if (firstBuild == BuildTarget.NO_INSTANT_RUN) {
            mProject.executor().withPackaging(packaging).run("assembleDebug");
            checkNormalApk(apk);
            startBuildId = null;
        } else {
            mProject.executor()
                    .withPackaging(packaging)
                    .withInstantRun(
                            firstBuild.getApiLevel(),
                            ColdswapMode.AUTO,
                            OptionalCompilationStep.FULL_APK)
                    .run("assembleDebug");
            InstantRunBuildInfo initialContext = InstantRunTestUtils.loadContext(instantRunModel);
            startBuildId = initialContext.getTimeStamp();
            checkApk(initialContext, firstBuild);
        }

        if (secondBuild == BuildTarget.NO_INSTANT_RUN) {
            mProject.executor().withPackaging(packaging).run("assembleDebug");
            checkNormalApk(apk);
        } else {
            mProject.executor()
                    .withPackaging(packaging)
                    .withInstantRun(
                            secondBuild.getApiLevel(),
                            ColdswapMode.AUTO,
                            OptionalCompilationStep.FULL_APK)
                    .run("assembleDebug");
            InstantRunBuildInfo buildContext = InstantRunTestUtils.loadContext(instantRunModel);
            assertThat(buildContext.getTimeStamp()).isNotEqualTo(startBuildId);
            checkApk(buildContext, secondBuild);
        }
    }

    private void checkApk(
            @NonNull InstantRunBuildInfo context,
            @NonNull BuildTarget target) throws Exception {
        switch (target) {
            case INSTANT_RUN_NO_COLD_SWAP:
                checkHotswappableApk(getMainApk(context.getArtifacts()));
                break;
            case INSTANT_RUN_MULTI_DEX:
                checkMultidexApk(getMainApk(context.getArtifacts()));
                break;
            case INSTANT_RUN_MULTI_APK:
                checkSplitApk(context.getArtifacts());
                break;
            default:
                throw new AssertionError("Should have called checkNormalApk directly");

        }
    }

    private void checkSplitApk(@NonNull List<InstantRunArtifact> artifacts) throws Exception {
        assertThat(artifacts.size()).named("Artifact count").isAtLeast(2);
        InstantRunArtifact main = artifacts.stream()
                .filter(artifact -> artifact.type == InstantRunArtifactType.SPLIT_MAIN)
                .findFirst().orElseThrow(() -> new AssertionError("Main artifact not found"));

        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(main.file);

        apkSubject.doesNotContainClass("Lcom/example/helloworld/HelloWorld;");
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
    }

    private void checkMultidexApk(@NonNull File apk) throws Exception {
        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(apk);

        apkSubject.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.INSTANT_RUN)
                .that().hasMethod("onCreate");
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.ALL);
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/AppInfo;",
                AbstractAndroidSubject.ClassFileScope.ALL);
    }

    private void checkHotswappableApk(@NonNull File apk) throws Exception {
        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(apk);

        apkSubject.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.MAIN)
                .that().hasMethod("onCreate");
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
        apkSubject.hasClass("Lcom/android/tools/fd/runtime/AppInfo;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
    }

    private void checkNormalApk(@NonNull File apk) throws Exception {
        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY).that(apk);

        apkSubject.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.MAIN)
                .that().hasMethod("onCreate");
        apkSubject.doesNotContainClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
        apkSubject.doesNotContainClass("Lcom/android/tools/fd/runtime/AppInfo;",
                AbstractAndroidSubject.ClassFileScope.MAIN);
    }


    private enum BuildTarget {
        NO_INSTANT_RUN(23),
        INSTANT_RUN_NO_COLD_SWAP(19),
        INSTANT_RUN_MULTI_DEX(23),
        INSTANT_RUN_MULTI_APK(24);

        private final int apiLevel;

        BuildTarget(int apiLevel) {
            this.apiLevel = apiLevel;
        }

        int getApiLevel() {
            return apiLevel;
        }
    }
}
