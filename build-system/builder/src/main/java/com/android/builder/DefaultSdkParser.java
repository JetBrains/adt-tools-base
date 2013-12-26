/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Properties;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FD_SUPPORT;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;
import static com.android.SdkConstants.FN_SOURCE_PROP;

/**
 * Default implementation of {@link SdkParser} for a normal Android SDK distribution.
 */
public class DefaultSdkParser implements SdkParser {

    private final String mSdkLocation;
    private final File mNdkLocation;
    private SdkManager mManager;

    private IAndroidTarget mTarget;
    private BuildToolInfo mBuildToolInfo;

    private File mTools;
    private File mPlatformTools;
    private File mAdb;
    private File mZipAlign;

    public DefaultSdkParser(@NonNull String sdkLocation, @Nullable File ndkLocation) {
        if (!sdkLocation.endsWith(File.separator)) {
            mSdkLocation = sdkLocation + File.separator;
        } else {
            mSdkLocation = sdkLocation;
        }
        mNdkLocation = ndkLocation;
    }

    @Override
    public void initParser(@NonNull String target,
                           @NonNull FullRevision buildToolRevision,
                           @NonNull ILogger logger) {
        if (mManager == null) {
            mManager = SdkManager.createManager(mSdkLocation, logger);
            if (mManager == null) {
                throw new IllegalStateException("failed to parse SDK!");
            }

            mTarget = mManager.getTargetFromHashString(target);
            if (mTarget == null) {
                throw new IllegalStateException("failed to find target " + target);
            }

            mBuildToolInfo = mManager.getBuildTool(buildToolRevision);
            if (mBuildToolInfo == null) {
                throw new IllegalStateException("failed to find Build Tools revision "
                        + buildToolRevision.toString());
            }
        }
    }

    @NonNull
    @Override
    public IAndroidTarget getTarget() {
        if (mManager == null) {
            throw new IllegalStateException("SdkParser was not initialized.");
        }
        return mTarget;
    }

    @NonNull
    @Override
    public BuildToolInfo getBuildTools() {
        if (mManager == null) {
            throw new IllegalStateException("SdkParser was not initialized.");
        }
        return mBuildToolInfo;
    }

    @Override
    @NonNull
    public String getAnnotationsJar() {
        return mSdkLocation + FD_TOOLS +
                '/' + FD_SUPPORT +
                '/' + FN_ANNOTATIONS_JAR;
    }

    @Override
    @Nullable
    public FullRevision getPlatformToolsRevision() {
        File platformTools = getPlatformToolsFolder();
        if (!platformTools.isDirectory()) {
            return null;
        }

        Reader reader = null;
        try {
            reader = new InputStreamReader(
                    new FileInputStream(new File(platformTools, FN_SOURCE_PROP)),
                    Charsets.UTF_8);
            Properties props = new Properties();
            props.load(reader);

            String value = props.getProperty(PkgProps.PKG_REVISION);

            return FullRevision.parseRevision(value);

        } catch (FileNotFoundException ignore) {
            // return null below.
        } catch (IOException ignore) {
            // return null below.
        } catch (NumberFormatException ignore) {
            // return null below.
        } finally {
            Closeables.closeQuietly(reader);
        }

        return null;
    }

    @Override
    @NonNull
    public File getZipAlign() {
        if (mZipAlign == null) {
            mZipAlign = new File(getToolsFolder(), SdkConstants.FN_ZIPALIGN);
        }
        return mZipAlign;
    }

    @Override
    @NonNull
    public File getAdb() {
        if (mAdb == null) {
            mAdb = new File(getPlatformToolsFolder(), SdkConstants.FN_ADB);
        }
        return mAdb;
    }

    @NonNull
    @Override
    public List<File> getRepositories() {
        List<File> repositories = Lists.newArrayList();

        File androidRepo = new File(mSdkLocation + "/extras/android/m2repository");
        if (androidRepo.isDirectory()) {
            repositories.add(androidRepo);
        }

        File googleRepo = new File(mSdkLocation + "/extras/google/m2repository");
        if (googleRepo.isDirectory()) {
            repositories.add(googleRepo);
        }

        return repositories;
    }

    @NonNull
    private File getPlatformToolsFolder() {
        if (mPlatformTools == null) {
            mPlatformTools = new File(mSdkLocation, FD_PLATFORM_TOOLS);
            if (!mPlatformTools.isDirectory()) {
                throw new IllegalStateException("Platform-tools folder missing: " +
                        mPlatformTools.getAbsolutePath());
            }
        }

        return mPlatformTools;
    }

    @NonNull
    private File getToolsFolder() {
        if (mTools == null) {
            mTools = new File(mSdkLocation, FD_TOOLS);
            if (!mTools.isDirectory()) {
                throw new IllegalStateException("Platform-tools folder missing: " +
                        mTools.getAbsolutePath());
            }
        }

        return mTools;
    }

    @Nullable
    @Override
    public File getNdkLocation() {
        return mNdkLocation;
    }
}
