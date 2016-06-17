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

/**
 * Extension for the {@code com.android.build.gradle.externalBuild.ExternalBuildPlugin}
 */
public class ExternalBuildExtension {

    String buildManifestPath;
    String executionRoot;

    public String getExecutionRoot() {
        return executionRoot;
    }

    public void setExecutionRoot(String executionRoot) {
        this.executionRoot = executionRoot;
    }

    public String getBuildManifestPath() {
        return buildManifestPath;
    }

    public void setBuildManifestPath(String buildManifestPath) {
        this.buildManifestPath = buildManifestPath;
    }
}
