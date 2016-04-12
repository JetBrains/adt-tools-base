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

import com.android.builder.model.OptionalCompilationStep;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.google.common.truth.Expect;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Check that the super classes can be found when subclassing things in provided dependencies.
 */
public class ProvidedSubclassTest {

    private static final AndroidTestApp TEST_APP = HelloWorldApp.forPlugin("com.android.application");

    static {
        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example/helloworld",
                "MyByteSink.java",
                "package com.example.helloworld;" +
                "public class MyByteSink extends com.google.common.io.ByteSink {\n" +
                "    public java.io.OutputStream openStream() {\n" +
                "        throw new RuntimeException();\n" +
                "    }\n" +
                "}\n"));
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(TEST_APP)
                    .create();

    @Rule
    public Expect expect = Expect.createAndEnableStackTrace();

    @Before
    public void addProvidedLibrary() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        TestFileUtils.appendToFile(project.getBuildFile(), "\n"
                + "dependencies {\n"
                + "    provided 'com.google.guava:guava:17.0'\n"
                + "}\n");


    }

    @Test
    public void checkV() throws Exception {
        project.execute("clean");
        project.execute(InstantRunTestUtils.getInstantRunArgs(23,
                ColdswapMode.DEFAULT, OptionalCompilationStep.RESTART_ONLY),
                "assembleDebug");

        // Check we can find the parent class.
        assertThat(project.getStderr()).doesNotContain("ByteSink");

    }


}
