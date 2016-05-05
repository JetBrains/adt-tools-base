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

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FN_ADB;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Helper for SDK related functions.
 */
public class SdkHelper {

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
        } else {
            System.out.println("ANDROID_HOME not set.");
        }

        return null;
    }

    @NonNull
    public static File getAapt() {
        return getBuildTool(
                Revision.parseRevision(
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION, Revision.Precision.MICRO),
                BuildToolInfo.PathId.AAPT);
    }

    @NonNull
    public static File getAapt(@NonNull Revision revision) {
        return getBuildTool(revision, BuildToolInfo.PathId.AAPT);
    }

    @NonNull
    public static File getDexDump() {
        return getBuildTool(
                Revision.parseRevision(
                        GradleTestProject.DEFAULT_BUILD_TOOL_VERSION, Revision.Precision.MICRO),
                BuildToolInfo.PathId.DEXDUMP);
    }

    @NonNull
    public static File getBuildTool(
            @NonNull Revision revision,
            @NonNull BuildToolInfo.PathId pathId) {
        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo buildToolInfo = AndroidSdkHandler.getInstance(findSdkDir())
                .getBuildToolInfo(revision, progress);
        if (buildToolInfo == null) {
            throw new RuntimeException("Test requires build-tools " + revision.toString());
        }
        return new File(buildToolInfo.getPath(pathId));
    }

    @NonNull
    public static File getAdb() {
        File adb = FileUtils.join(findSdkDir(), FD_PLATFORM_TOOLS, FN_ADB);
        if (!adb.exists()) {
            throw new RuntimeException("Unable to find adb.");
        }
        return adb;
    }

    /**
     * Returns a {@link IAndroidTarget} with a minimum api level.
     * @param minimumApiLevel the desired api level.
     * @return the IAndroidTarget of that api level or above or null if not found.
     */
    @Nullable
    public static IAndroidTarget getTarget(int minimumApiLevel) {
        FakeProgressIndicator progressIndicator = new FakeProgressIndicator();
        IAndroidTarget target = AndroidSdkHandler.getInstance(findSdkDir())
                .getAndroidTargetManager(progressIndicator)
                .getTargetOfAtLeastApiLevel(minimumApiLevel, progressIndicator);
        return target;
    }
}
