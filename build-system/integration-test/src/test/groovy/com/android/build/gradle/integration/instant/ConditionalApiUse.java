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

import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexClassSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.truth.IndirectSubject;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Test that applications that use APIs not available in the minSdkVersion are properly
 * instrumented and packaged depending on the target deployment version.
 */
public class ConditionalApiUse {

    @Rule
    public final Adb adb = new Adb();

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("conditionalApiUse")
            .create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void buildFor19() throws Exception {

        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(
                project, Packaging.NEW_PACKAGING, 19, ColdswapMode.AUTO);

        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug"));

        IndirectSubject<DexClassSubject> myCameraAccessExceptionClass = apkSubject
                .hasClass("Lcom/android/tests/conditionalApiUse/MyException;",
                        AbstractAndroidSubject.ClassFileScope.MAIN);

        // since we are building for 19, and the super class of MyException did not
        // exist at that release level, it should not be instrumented.
        myCameraAccessExceptionClass.that().doesNotHaveField("$change");

        makeHotswapCompatibleChange();

        project.executor()
                .withInstantRun(19, ColdswapMode.AUTO)
                .withPackaging(Packaging.NEW_PACKAGING)
                .run("assembleDebug");

        // because we touched a class that was not compatible with InstantRun, we should have
        // a coldswap event.
        InstantRunBuildInfo buildInfo = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(buildInfo.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.INSTANT_RUN_DISABLED.toString());

        assertThat(buildInfo.getArtifacts()).hasSize(1);
        assertThat(buildInfo.getArtifacts().get(0).type).isEqualTo(InstantRunArtifactType.MAIN);
    }

    @Test
    public void buildFor23() throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(
                project, Packaging.NEW_PACKAGING, 23, ColdswapMode.AUTO);

        ApkSubject apkSubject = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug"));

        IndirectSubject<DexClassSubject> myCameraAccessExceptionClass = apkSubject
                .hasClass("Lcom/android/tests/conditionalApiUse/MyException;",
                        AbstractAndroidSubject.ClassFileScope.INSTANT_RUN);

        // since we are building for 23, and the super class exists, the exception class should
        // be instrumented.
        myCameraAccessExceptionClass.that().hasField("$change");

        makeHotswapCompatibleChange();

        project.executor()
                .withInstantRun(23, ColdswapMode.AUTO)
                .withPackaging(Packaging.NEW_PACKAGING)
                .run("assembleDebug");

        InstantRunArtifact reloadDexArtifact = InstantRunTestUtils
                .getReloadDexArtifact(instantRunModel);

        expect.about(DexFileSubject.FACTORY)
                .that(reloadDexArtifact.file)
                .hasClass("Lcom/android/tests/conditionalApiUse/MyException$override;")
                .that().hasMethod("toString");
    }

    private void makeHotswapCompatibleChange() throws IOException {
        String updatedClass = "package com.android.tests.conditionalApiUse;\n"
                + "\n"
                + "import android.hardware.camera2.CameraAccessException;\n"
                + "import android.os.Build;\n"
                + "\n"
                + "public class MyException extends CameraAccessException {\n"
                + "\n"
                + "    public MyException(int problem, String message, Throwable cause) {\n"
                + "        super(problem, message, cause);\n"
                + "    }\n"
                + "\n"
                + "    public String toString() {\n"
                + "        return \"Some string\";\n"
                + "    }\n"
                + "}";
        Files.write(updatedClass,
                project.file("src/main/java/com/android/tests/conditionalApiUse/MyException.java"),
                Charsets.UTF_8);
    }
}
