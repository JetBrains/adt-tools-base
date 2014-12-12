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

import com.android.annotations.Nullable
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.ide.common.internal.CommandLineRunner
import com.android.ide.common.internal.LoggedErrorException
import com.android.sdklib.repository.FullRevision
import com.android.utils.StdLogger
import com.google.common.collect.Lists
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static org.junit.Assert.fail

/**
 * Assemble tests for maxSdkVersion.
 */
class MaxSdkVersionTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("maxSdkVersion")
            .create()

    @BeforeClass
    static void setUp() {
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void maxSdkVersion() {
        checkMaxSdkVersion(project.getApk("f1", "debug"), "21")
        checkMaxSdkVersion(project.getApk("f2", "debug"), "19")
    }

    private void checkMaxSdkVersion(File testApk, String version)
            throws InterruptedException, LoggedErrorException, IOException {

        File aapt = SdkHelper.getAapt(FullRevision.parseRevision("19.1.0"));

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";
        command[3] = testApk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        final List<String> aaptOutput = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        System.out.println("Beginning dump");
        for (String line : aaptOutput) {
            if (line.equals("maxSdkVersion:'" + version + "'")) {
                return;
            }
        }
        fail("Could not find uses-sdk:maxSdkVersion set to " + version + " in apk dump");
    }
}
