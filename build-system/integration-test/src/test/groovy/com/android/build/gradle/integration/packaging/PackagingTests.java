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

/**
 * Utilities common to all packaging tests.
 */
public class PackagingTests {

    public static void checkZipAlign(File apk, String... additionalFlags)
            throws IOException, InterruptedException {
        ImmutableList.Builder<String> args = ImmutableList.builder();

        args.add(SdkHelper.getBuildTool(BuildToolInfo.PathId.ZIP_ALIGN).getAbsolutePath());
        args.add("-c"); // check
        args.add("-v"); // verbose
        args.add(additionalFlags);
        args.add("4"); // default alignment - you always have to use it with zipalign
        args.add(apk.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(args.build());
        Truth.assertThat(processBuilder.start().waitFor())
                .named("zipalign return code")
                .isEqualTo(0);
    }

    public static void checkZipAlignWithPageAlignedSoFiles(File apk)
            throws IOException, InterruptedException {
        checkZipAlign(apk, "-p");
    }
}
