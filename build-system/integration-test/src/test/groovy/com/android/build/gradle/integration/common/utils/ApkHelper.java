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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.StdLogger;

import org.gradle.api.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Helper to help read/test the content of generated apk file.
 */
public class ApkHelper {
    public static void checkVersion(
            @NonNull File apk,
            @Nullable Integer code)
            throws IOException, InterruptedException, LoggedErrorException {
        checkVersion(apk, code, null /* versionName */);
    }

    public static void checkVersionName(
        @NonNull File apk,
        @Nullable String name)
        throws IOException, InterruptedException, LoggedErrorException {
        checkVersion(apk, null, name);
    }

    public static void checkVersion(
            @NonNull File apk,
            @Nullable Integer code,
            @Nullable String versionName)
            throws IOException, InterruptedException, LoggedErrorException {
        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(
                SdkHelper.getAapt(FullRevision.parseRevision("20.0.0")),
                commandLineRunner);

        ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apk);

        if (code != null) {
            assertEquals("Unexpected version code for split: " + apk.getName(),
                    code, apkInfo.getVersionCode());
        }

        if (versionName != null) {
            assertEquals("Unexpected version code for split: " + apk.getName(),
                    versionName, apkInfo.getVersionName());
        }
    }
}
