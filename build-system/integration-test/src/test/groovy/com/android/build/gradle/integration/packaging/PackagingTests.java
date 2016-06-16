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

package com.android.build.gradle.integration.packaging;

import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.sdklib.BuildToolInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Utilities common to all packaging tests.
 */
public class PackagingTests {

    public static void checkZipAlign(File apk) throws IOException, InterruptedException {
        List<String> args =
                ImmutableList.of(
                        SdkHelper.getBuildTool(BuildToolInfo.PathId.ZIP_ALIGN).getAbsolutePath(),
                        "-c", // check
                        "-v", // verbose
                        "-p", // page aligned so files
                        "4", // 4 - you always have to use it with zipalign
                        apk.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        Truth.assertThat(processBuilder.start().waitFor())
                .named("zipalign return code")
                .isEqualTo(0);
    }
}
