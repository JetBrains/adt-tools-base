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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Checking if multidex and non-multidex variant requiring the same remote library will
 * package properly.
 */
public class MultiDexCacheTest {

    @Rule
    public GradleTestProject mProject = GradleTestProject.builder()
            .fromTestProject("flavored")
            .create();

    @Test
    public void checkMultiDexToPreDexCache() throws Exception {
        setMultiDexFlavors(true, false);
        mProject.execute("clean", "assembleDebug");

        verifyGuavaClassesExist();
    }

    @Test
    public void checkPreDexToMultiDexCache() throws Exception {
        setMultiDexFlavors(false, true);
        mProject.execute("clean", "assembleDebug");

        verifyGuavaClassesExist();
    }

    private void verifyGuavaClassesExist() throws Exception {
        assertThatApk(mProject.getApk("f1", "debug"))
                .containsClass("Lcom/google/common/collect/Maps;");

        assertThatApk(mProject.getApk("f2", "debug"))
                .containsClass("Lcom/google/common/collect/Maps;");
    }

    private void setMultiDexFlavors(
            boolean isF1Multidex, boolean isF2Multidex) throws IOException {
        TestFileUtils.appendToFile(
                mProject.getBuildFile(),
                "android {\n"
                        + "    productFlavors {\n"
                        + "        f1 {"
                        + "            minSdkVersion 21\n"
                        + "            multiDexEnabled " + Boolean.toString(isF1Multidex) +"\n"
                        + "        }\n"
                        + "        f2 {\n"
                        + "            minSdkVersion 21\n"
                        + "            multiDexEnabled " + Boolean.toString(isF2Multidex) +"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile 'com.google.guava:guava:18.0'\n"
                        + "}");
    }
}
