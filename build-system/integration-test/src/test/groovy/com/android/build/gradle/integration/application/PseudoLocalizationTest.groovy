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
import com.android.sdklib.repository.FullRevision
import com.android.utils.StdLogger
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.junit.Assert.assertTrue

/**
 * Assemble tests for emptySplit.
 */
class PseudoLocalizationTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromSample("pseudolocalized")
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
    void lint() {
        project.execute("lint")
    }

    class TestOutput extends CommandLineRunner.CommandLineOutput {
        public boolean pseudolocalized = false;
        private Pattern p = Pattern.compile("^locales:.*'en[_-]XA'.*'ar[_-]XB'.*");

        @Override
        public void out(@Nullable String line) {
            if (line != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    pseudolocalized = true;
                }
            }
        }
        @Override
        public void err(@Nullable String line) {
            super.err(line);

        }

        public boolean getPseudolocalized() {
            return pseudolocalized;
        }
    };


    @Test
    public void testPseudolocalization() throws Exception {
        File aapt = SdkHelper.getAapt(FullRevision.parseRevision("21.1.0"));

        File apk = project.getApk("debug");

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";
        command[3] = apk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        TestOutput handler = new TestOutput();
        commandLineRunner.runCmdLine(command, handler, null /*env vars*/);

        assertTrue("Pseudo locales were not added", handler.getPseudolocalized());
    }

}
