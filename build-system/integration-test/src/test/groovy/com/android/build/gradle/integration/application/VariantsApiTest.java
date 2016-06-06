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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for all the methods exposed in the so-called variants API.
 */
public class VariantsApiTest {

    @Rule
    public GradleTestProject mProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                mProject.getBuildFile(),
                //language=groovy
                "android {\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        assert variant.assemble != null\n"
                        + "        variant.outputs.each { output -> \n"
                        + "            assert output.assemble != null\n"
                        + "        }\n"
                        + "    }\n"
                        + "    \n"
                        + "    testVariants.all { variant ->\n"
                        + "        assert variant.testedVariant != null\n"
                        + "        assert variant.assemble != null\n"
                        + "        variant.outputs.each { output ->\n"
                        + "            assert output.assemble != null\n"
                        + "        }\n"
                        + "    }\n"
                        + "    \n"
                        + "    unitTestVariants.all { variant ->\n"
                        + "        assert variant.testedVariant != null\n"
                        + "    }\n"
                        + "}");
    }

    @Test
    public void buildScriptRuns() throws Exception {
        mProject.execute("clean");

        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(FileUtils.sha1(mProject.file("build.gradle")))
                .isEqualTo("9bb80c80cb93a4e132826f9e2fe62e391fb66245");
    }
}
