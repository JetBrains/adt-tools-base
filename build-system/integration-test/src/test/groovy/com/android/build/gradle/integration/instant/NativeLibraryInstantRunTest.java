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

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.thatUsesArt;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ddmlib.IDevice;
import com.android.builder.packaging.PackagingUtils;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

/**
 * Simple test to ensure component model plugin do not crash when instant run is enabled.
 */
@RunWith(MockitoJUnitRunner.class)
public class NativeLibraryInstantRunTest {

    @Rule
    public Adb adb = new Adb();

    private final ILogger iLogger = new StdLogger(StdLogger.Level.INFO);

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "project.ext['android.useDeprecatedNdk'] = true"
                        + "\napply plugin: \"com.android.application\"\n"
                        + "android {\n"
                        + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            moduleName 'hello-jni'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    @Category(DeviceTests.class)
    public void checkRuns() throws Exception {
        IDevice device = adb.getDevice(thatUsesArt());
        AndroidProject model = project.model().getSingle();
        InstantRun instantRunModel = InstantRunTestUtils.getInstantRunModel(model);
        project.executor()
                .withInstantRun(21, ColdswapMode.DEFAULT, OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");
        InstantRunBuildInfo info = InstantRunTestUtils.loadContext(instantRunModel);
        InstantRunTestUtils.doInstall(device, info.getArtifacts());

        // Run app
        InstantRunTestUtils.unlockDevice(device);

        InstantRunTestUtils.runApp(device, "com.example.hellojni/.HelloJni");


        long token = PackagingUtils.computeApplicationHash(model.getBuildFolder());

        //Connect to device
        InstantRunClient client =
                new InstantRunClient("com.example.hellojni", iLogger, token, 8125);

        // Give the app a chance to start
        InstantRunTestUtils.waitForAppStart(client, device);

        device.uninstallPackage("com.example.hellojni");
    }

}
