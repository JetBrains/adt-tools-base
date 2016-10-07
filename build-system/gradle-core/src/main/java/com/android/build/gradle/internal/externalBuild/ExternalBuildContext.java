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

import com.android.builder.core.AndroidBuilder;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains all contextual and intermediaries build artifacts.
 */
public class ExternalBuildContext {

    private final ExternalBuildExtension externalBuildExtension;
    private List<File> inputJarFiles = new ArrayList<>();
    private ExternalBuildApkManifest.ApkManifest buildManifest;
    private AndroidBuilder androidBuilder;
    private File executionRoot;

    public ExternalBuildContext(ExternalBuildExtension externalBuildExtension) {
        this.externalBuildExtension = externalBuildExtension;
    }

    public void setInputJarFiles(java.util.List<File> inputJarFiles) {
        this.inputJarFiles = inputJarFiles;
    }

    public List<File> getInputJarFiles() {
        return inputJarFiles;
    }

    public void setExecutionRoot(File executionRoot) {
        this.executionRoot = executionRoot;
    }

    public File getExecutionRoot() {
        return executionRoot;
    }

    public ExternalBuildExtension getExternalBuildExtension() {
        return externalBuildExtension;
    }

    public void setBuildManifest(ExternalBuildApkManifest.ApkManifest buildManifest) {
        this.buildManifest = buildManifest;
    }

    public ExternalBuildApkManifest.ApkManifest getBuildManifest() {
        return buildManifest;
    }

    public void setAndroidBuilder(AndroidBuilder androidBuilder) {
        this.androidBuilder = androidBuilder;
    }

    public AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }
}
