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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Tests for all the methods exposed in the so-called variants API.
 */
@RunWith(FilterableParameterized.class)
public class VariantsApiTest {

    private static final String VARIANTS_API_SNIPPET =
            "android {\n"
                    + "    %s.all { variant ->\n"
                    + "        assert variant.assemble != null\n"
                    + "        variant.outputs.each { mainOutput -> \n"
                    + "            assert mainOutput.assemble != null\n"
                    + "            assert mainOutput.processManifest != null\n"
                    + "            assert mainOutput.processResources != null\n"
                    + "        }\n"
                    + "    }\n"
                    + "    \n"
                    + "    testVariants.all { variant ->\n"
                    + "        assert variant.testedVariant != null\n"
                    + "        assert variant.assemble != null\n"
                    + "        variant.outputs.each { testOutput ->\n"
                    + "            assert testOutput.assemble != null\n"
                    + "            assert testOutput.processManifest != null\n"
                    + "            assert testOutput.processResources != null\n"
                    + "        }\n"
                    + "    }\n"
                    + "    \n"
                    + "    unitTestVariants.all { variant ->\n"
                    + "        assert variant.testedVariant != null\n"
                    + "    }\n"
                    + "}";

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return ImmutableList.of(
                new Object[]{"com.android.application", "applicationVariants"},
                new Object[]{"com.android.library", "libraryVariants"});
    }

    @Rule
    public GradleTestProject project;

    private String dslProperty;

    public VariantsApiTest(String plugin, String dslProperty) {
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldApp.forPlugin(plugin))
                        .create();

        this.dslProperty = dslProperty;
    }


    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(VARIANTS_API_SNIPPET, this.dslProperty));
    }

    @Test
    public void buildScriptRuns() throws Exception {
        project.execute("clean");

        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        HashCode hashCode = Hashing.sha1().hashString(VARIANTS_API_SNIPPET, StandardCharsets.UTF_8);
        assertThat(hashCode.toString()).isEqualTo("8b2f82a3cb8b14b86236243e085261e3359108cf");
    }
}
