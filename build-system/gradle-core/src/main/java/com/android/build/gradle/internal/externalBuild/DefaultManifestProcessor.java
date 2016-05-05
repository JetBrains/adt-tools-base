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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.sdk.TargetInfo;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.Project;

import java.io.File;

/**
 * Default implementation of the {@link ExternalBuildProcessor} interface.
 */
public class DefaultManifestProcessor implements ExternalBuildProcessor {

    private final Project mProject;

    DefaultManifestProcessor(Project project) {
        mProject = project;
    }

    @Override
    public void process(@NonNull ExternalBuildApkManifest.ApkManifest apkManifest) {
        ExternalBuildApkManifest.AndroidSdk sdk = apkManifest.getAndroidSdk();

        BuildToolInfo buildToolInfo = BuildToolInfo.partial(
                // Just make AndroidBuilder happy.
                AndroidBuilder.MIN_BUILD_TOOLS_REV,
                mProject.getProjectDir(),
                ImmutableMap.of(
                        BuildToolInfo.PathId.DX, getAbsolutePath(sdk.getDx())));

        IAndroidTarget androidTarget =
                new ExternalBuildAndroidTarget(getAbsolutePath(sdk.getAndroidJar()));

        TargetInfo targetInfo = new TargetInfo(androidTarget, buildToolInfo);

        AndroidBuilder androidBuilder = new AndroidBuilder(
                mProject.getPath(),
                "Android Studio + external build system",
                new GradleProcessExecutor(mProject),
                new GradleJavaProcessExecutor(mProject),
                new ExtraModelInfo(mProject, false),
                new LoggerWrapper(mProject.getLogger()),
                false);

        androidBuilder.setTargetInfo(targetInfo);
        // do nothing so far.
    }

    /**
     * Turns a relative path into an absolute one.
     */
    private File getAbsolutePath(String path) {
        // TODO: Is the gradle project going to use the same directory as the other build system?
        return new File(mProject.getProjectDir(), path);
    }
}
