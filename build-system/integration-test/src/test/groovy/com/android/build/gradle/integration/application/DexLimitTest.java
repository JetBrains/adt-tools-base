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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.utils.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class DexLimitTest {

    @Parameterized.Parameters(name="dexInProcess={0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {{false}, {true}});
    }

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
            .fromTestApp(TEST_APP).withHeap("2G").create();

    private final boolean mDexInProcess;

    public DexLimitTest(boolean dexInProcess) {
        mDexInProcess = dexInProcess;
    }

    @Before
    public void disableDexInProcess() throws IOException {
        if (mDexInProcess) {
            DexInProcessHelper.disableDexInProcess(mProject.getBuildFile());
        }
    }

    @Test
    public void checkDexErrorMessage() throws Exception {
        GradleBuildResult result = mProject.executor().expectFailure().run("assembleDebug");
        if (GradleTestProject.USE_JACK) {
            assertThat(result.getStderr()).contains(
                    "Dex writing phase: classes.dex has too many IDs.");
        } else {
            assertThat(result.getStderr()).contains(
                    "https://developer.android.com/tools/building/multidex.html");
        }

        // Check that when dexing in-process, we don't keep bad state after a failure
        if (mDexInProcess) {
            FileUtils.delete(mProject.file("src/main/java/com/example/A.java"));
            mProject.execute("assembleDebug");

            File apk = mProject.getApk("debug");
            assertThatApk(apk).doesNotContainClass("Lcom/example/A;");
            assertThatApk(apk).containsClass("Lcom/example/B;");
        }
    }
}
