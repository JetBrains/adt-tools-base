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

import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.DexFileSubject;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ide.common.packaging.PackagingUtils;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.UserFeedback;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Expect;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Smoke test for hot swap builds.
 */
@RunWith(MockitoJUnitRunner.class)
public class HotSwapTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create();

    @Rule
    public Expect expect = Expect.create();

    @Mock
    UserFeedback userFeedback;

    @Mock
    ILogger iLogger;

    @Before
    public void activityClass() throws IOException {
        createActivityClass("", "");
    }

    @Test
    public void buildIncrementallyWithInstantRun() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel = InstantRunTestUtils
                .getInstantRunModel(project.getSingleModel());

        project.execute(
                InstantRunTestUtils.getInstantRunArgs(19,
                        ColdswapMode.MULTIAPK, OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");

        // As no injected API level, will default to no splits.
        DexFileSubject dexFile = expect.about(ApkSubject.FACTORY)
                .that(project.getApk("debug")).hasMainDexFile().that();
        dexFile.hasClass("Lcom/example/helloworld/HelloWorld;")
                .that().hasMethod("onCreate");
        dexFile.hasClass("Lcom/android/tools/fd/runtime/BootstrapApplication;");

        checkHotSwapCompatibleChange(instantRunModel);
    }

    @Test
    public void testModel() throws Exception {
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.getSingleModel());

        assertTrue(instantRunModel.isSupportedByArtifact());

        TestFileUtils.appendToFile(project.getBuildFile(), "\nandroid.buildTypes.debug.minifyEnabled = true");

        instantRunModel = InstantRunTestUtils.getInstantRunModel(
                project.getSingleModel());

        assertFalse(instantRunModel.isSupportedByArtifact());
    }

    @Test
    @Category(DeviceTests.class)
    public void connectToInstantRunServer() throws Exception {
        project.execute("clean");
        // Open project in simulated IDE
        AndroidProject model = project.getSingleModel();
        long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);

        // Run first time on device
        IDevice device = DeviceHelper.getIDevice();
        Assume.assumeThat("Device api level", device.getVersion(), is(new AndroidVersion(23, null)));

        project.execute(
                InstantRunTestUtils.getInstantRunArgs(
                        device, ColdswapMode.MULTIAPK, OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");

        // Deploy to device
        InstantRunBuildContext context = new InstantRunBuildContext();
        context.loadFromXmlFile(instantRunModel.getInfoFile());
        assertNotNull("Last build should exist", context.getLastBuild());
        InstantRunTestUtils.doInstall(device, context.getLastBuild().getArtifacts());

        // Run app
        InstantRunTestUtils.unlockDevice(device);
        InstantRunTestUtils.runApp(device, "com.example.helloworld/.HelloWorld");

        //Connect to device
        InstantRunClient client =
                new InstantRunClient("com.example.helloworld", userFeedback, iLogger, token, 8125);

        // Give the app a chance to start
        Thread.sleep(1000); // TODO: Is there a way to determine that the app is ready?

        AppState appState = client.getAppState(device);
        assertThat(appState).isEqualTo(AppState.FOREGROUND);

        // Clean up
        device.uninstallPackage("com.example.helloworld");
    }

    /**
     * Check a hot-swap compatible change works as expected.
     */
    private void checkHotSwapCompatibleChange(@NonNull InstantRun instantRunModel) throws Exception {
        createActivityClass("import java.util.logging.Logger;",
                "Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(\"Added some logging\");");

        project.execute(InstantRunTestUtils.getInstantRunArgs(),
                instantRunModel.getIncrementalAssembleTaskName());

        InstantRunBuildContext context = InstantRunTestUtils.loadContext(instantRunModel);

        assertNotNull(context.getLastBuild());
        assertThat(context.getLastBuild().getArtifacts()).hasSize(1);

        InstantRunBuildContext.Artifact artifact =
                Iterables.getOnlyElement(context.getLastBuild().getArtifacts());

        assertThat(artifact.getType()).isEqualTo(InstantRunBuildContext.FileType.RELOAD_DEX);

        expect.about(DexFileSubject.FACTORY)
                .that(artifact.getLocation())
                .hasClass("Lcom/example/helloworld/HelloWorld$override;")
                .that().hasMethod("onCreate");
    }

    private void createActivityClass(String imports, String newMethodBody)
            throws IOException {
        String javaCompile = "package com.example.helloworld;\n" + imports +
                "\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "\n"
                + "public class HelloWorld extends Activity {\n"
                + "    /** Called when the activity is first created. */\n"
                + "    @Override\n"
                + "    public void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.main);\n"
                + "        " +
                newMethodBody +
                "    }\n"
                + "}";
        Files.write(javaCompile,
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                Charsets.UTF_8);
    }

}
