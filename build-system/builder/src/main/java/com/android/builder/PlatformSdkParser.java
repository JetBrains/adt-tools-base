/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.FakeAndroidTarget;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * Implementation of {@link SdkParser} for the SDK prebuilds in the Android source tree.
 */
public class PlatformSdkParser implements SdkParser {
    private final String mPlatformRootFolder;

    private boolean mInitialized = false;
    private IAndroidTarget mTarget;
    private BuildToolInfo mBuildToolInfo;

    private File mHostTools;
    private File mZipAlign;
    private File mAdb;

    public PlatformSdkParser(@NonNull String sdkLocation) {
        mPlatformRootFolder = sdkLocation;
    }

    @Override
    public void initParser(@NonNull String target,
                           @NonNull FullRevision buildToolRevision,
                           @NonNull ILogger logger) {
        if (!mInitialized) {
            mTarget = new FakeAndroidTarget(mPlatformRootFolder, target);

            mBuildToolInfo = new BuildToolInfo(buildToolRevision, new File(mPlatformRootFolder),
                    new File(getHostToolsFolder(), SdkConstants.FN_AAPT),
                    new File(getHostToolsFolder(), SdkConstants.FN_AIDL),
                    new File(mPlatformRootFolder, "prebuilts/sdk/tools/dx"),
                    new File(mPlatformRootFolder, "prebuilts/sdk/tools/lib/dx.jar"),
                    new File(getHostToolsFolder(), SdkConstants.FN_RENDERSCRIPT),
                    new File(mPlatformRootFolder, "prebuilts/sdk/renderscript/include"),
                    new File(mPlatformRootFolder, "prebuilts/sdk/renderscript/clang-include"),
                    new File(getHostToolsFolder(), SdkConstants.FN_BCC_COMPAT),
                    new File(getHostToolsFolder(), "arm-linux-androideabi-ld"),
                    new File(getHostToolsFolder(), "i686-linux-android-ld"),
                    new File(getHostToolsFolder(), "mipsel-linux-android-ld"));
            mInitialized = true;
        }
    }

    @NonNull
    @Override
    public IAndroidTarget getTarget() {
        if (!mInitialized) {
            throw new IllegalStateException("SdkParser was not initialized.");
        }
        return mTarget;
    }

    @NonNull
    @Override
    public BuildToolInfo getBuildTools() {
        if (!mInitialized) {
            throw new IllegalStateException("SdkParser was not initialized.");
        }
        return mBuildToolInfo;
    }

    @Override
    @NonNull
    public String getAnnotationsJar() {
        String host;
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            host = "darwin-x86";
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            host = "linux";
        } else {
            throw new IllegalStateException("Windows is not supported for platform development");
        }

        return mPlatformRootFolder + "/out/host/" + host + "/framework/annotations.jar";
    }

    @Override
    @Nullable
    public FullRevision getPlatformToolsRevision() {
        return new FullRevision(99);
    }

    @Override
    @NonNull
    public File getZipAlign() {
        if (mZipAlign == null) {
            mZipAlign = new File(getHostToolsFolder(), SdkConstants.FN_ZIPALIGN);
        }

        return mZipAlign;
    }

    @Override
    @NonNull
    public File getAdb() {
        if (mAdb == null) {

            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                mAdb = new File(mPlatformRootFolder, "out/host/darwin-x86/bin/adb");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
                mAdb = new File(mPlatformRootFolder, "out/host/linux-x86/bin/adb");
            } else {
                throw new IllegalStateException(
                        "Windows is not supported for platform development");
            }
        }

        return mAdb;
    }

    @NonNull
    @Override
    public List<File> getRepositories() {
        List<File> repositories = Lists.newArrayList();
        repositories.add(new File(mPlatformRootFolder + "/prebuilts/sdk/m2repository"));

        return repositories;
    }

    private File getHostToolsFolder() {
        if (mHostTools == null) {
            File tools = new File(mPlatformRootFolder, "prebuilts/sdk/tools");
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
                mHostTools = new File(tools, "darwin");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
                mHostTools = new File(tools, "linux");
            } else {
                throw new IllegalStateException(
                        "Windows is not supported for platform development");
            }

            if (!mHostTools.isDirectory()) {
                throw new IllegalStateException("Host tools folder missing: " +
                        mHostTools.getAbsolutePath());
            }
        }
        return mHostTools;
    }

    @Nullable
    @Override
    public File getNdkLocation() {
        return null;
    }
}
