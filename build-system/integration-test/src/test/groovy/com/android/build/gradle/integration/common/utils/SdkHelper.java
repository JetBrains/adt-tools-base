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

import com.android.annotations.NonNull;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import java.io.File;

/**
 * Helper for SDK related functions.
 */
public class SdkHelper {

    private static final String BUILD_TOOLS_VERSION = "21.1.1";

    /**
     * Returns the SDK folder as built from the Android source tree.
     */
    public static File findSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find SDK in ANDROID_HOME=" + androidHome);
            }
        }
        return null;
    }

    @NonNull
    public static File getAapt() {
        return getBuildTool(
                FullRevision.parseRevision(BUILD_TOOLS_VERSION),
                BuildToolInfo.PathId.AAPT);
    }

    @NonNull
    public static File getAapt(@NonNull FullRevision fullRevision) {
        return getBuildTool(fullRevision, BuildToolInfo.PathId.AAPT);
    }

    @NonNull
    public static File getDexDump() {
        return getBuildTool(
                FullRevision.parseRevision(BUILD_TOOLS_VERSION),
                BuildToolInfo.PathId.DEXDUMP);
    }

    @NonNull
    public static File getBuildTool(
            @NonNull FullRevision fullRevision,
            @NonNull BuildToolInfo.PathId pathId) {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        SdkManager sdkManager = SdkManager.createManager(findSdkDir().getAbsolutePath(), logger);
        assert sdkManager != null;
        BuildToolInfo buildToolInfo = sdkManager.getBuildTool(fullRevision);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools " + fullRevision.toString());
        }
        return new File(buildToolInfo.getPath(pathId));
    }
}
