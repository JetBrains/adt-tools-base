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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class DexLimitTest {

    private static final String CLASS_NAME = "com/example/B";
    private static final String CLASS_FULL_TYPE = "L" + CLASS_NAME + ";";
    private static final String CLASS_SRC_LOCATION = "src/main/java/" + CLASS_NAME + ".java";

    private static final String CLASS_CONTENT =  "\n"
            + "package com.example;\n"
            + "public class B { }";

    private static final AndroidTestApp TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    static {
        StringBuilder classFileBuilder = new StringBuilder();
        for (int i=0; i<65536/2; i++) {
            classFileBuilder.append("    public void m").append(i).append("() {}\n");
        }

        String methods = classFileBuilder.toString();

        String classFileA = "package com.example;\npublic class A {\n" + methods + "\n}";
        String classFileB = "package com.example;\npublic class B {\n" + methods + "\n}";

        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example", "A.java", classFileA));
        TEST_APP.addFile(new TestSourceFile("src/main/java/com/example", "B.java", classFileB));
    }

    @Rule
    public final GradleTestProject mProject = GradleTestProject.builder()
            .fromTestApp(TEST_APP).captureStdErr(true).withHeap("2G").create();

    @Test
    public void checkDexErrorMessage() throws Exception {
        mProject.getStderr().reset();
        mProject.executeExpectingFailure("assembleDebug");
        assertThat(mProject.getStderr().toString()).contains(
                "https://developer.android.com/tools/building/multidex.html");
    }
}
