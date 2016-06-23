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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.OptionalCompilationStep;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunClient;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;

/**
 * Smoke test for hot swap builds.
 */
@RunWith(MockitoJUnitRunner.class)
public class NoCodeChangeAfterCompatibleChangeTest {

    private static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;

    private static final String LOG_TAG = "NoCodeChangeAfterCompatibleChangeTest";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void activityClass() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        createActivityClass("Original");
    }

    @Test
    public void testMultidex() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(project.model().getSingle());

        project.executor().withInstantRun(23, COLDSWAP_MODE, OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        ApkSubject apkFile = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug"));
        apkFile.hasClass("Lcom/example/helloworld/HelloWorld;",
                AbstractAndroidSubject.ClassFileScope.INSTANT_RUN)
                .that().hasMethod("onCreate");
        apkFile.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;",
                AbstractAndroidSubject.ClassFileScope.ALL);

        makeHotSwapChange();

        // force the DEX creation.
        project.executor().withInstantRun(23, COLDSWAP_MODE, OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");

        List<InstantRunArtifact> dexFiles = InstantRunTestUtils
                .getCompiledColdSwapChange(instantRunModel, COLDSWAP_MODE);
        assertThat(dexFiles).hasSize(1);

        // now run again the incremental build.
        project.executor().withInstantRun(23, COLDSWAP_MODE).run("assembleDebug");

        InstantRunBuildContext instantRunBuildContext = InstantRunTestUtils
                .loadBuildContext(23, instantRunModel);

        assertThat(instantRunBuildContext.getLastBuild().getArtifacts()).hasSize(0);
        assertThat(instantRunBuildContext.getLastBuild().getVerifierStatus()).isAbsent();
    }

    private void makeHotSwapChange() throws IOException {
        createActivityClass("HOT SWAP!");
    }

    private void createActivityClass(String message)
            throws IOException {
        String javaCompile = "package com.example.helloworld;\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import java.util.logging.Logger;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        Logger.getLogger(\"" + LOG_TAG + "\")\n"
                + "                .warning(\"" + message + "\");"
                + "    }\n"
                + "}\n";
        Files.write(javaCompile,
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                Charsets.UTF_8);
    }

}
