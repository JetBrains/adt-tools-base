/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Implementation of {@link NativeAndroidProject}.
 */
public class NativeAndroidProjectImpl implements NativeAndroidProject, Serializable {

    private static final long serialVersionUID = 1L;

    private final int apiVersion;
    @NonNull
    private final String modelVersion;
    @NonNull
    private final String name;
    @NonNull
    private final Collection<File> buildFiles;
    @NonNull
    private final Collection<NativeArtifact> artifacts;
    @NonNull
    private final Collection<NativeToolchain> toolChains;
    @NonNull
    private final Collection<NativeSettings> settings;
    @NonNull
    private final Map<String, String> fileExtensions;
    @NonNull
    private final Collection<String> buildSystems;

    public NativeAndroidProjectImpl(
            @NonNull String modelVersion,
            @NonNull String name,
            @NonNull Collection<File> buildFiles,
            @NonNull Collection<NativeArtifact> artifacts,
            @NonNull Collection<NativeToolchain> toolChains,
            @NonNull Collection<NativeSettings> settings,
            @NonNull Map<String, String> fileExtensions,
            @NonNull Collection<String> buildSystems,
            int apiVersion) {
        this.modelVersion = modelVersion;
        this.name = name;
        this.buildFiles = buildFiles;
        this.artifacts = artifacts;
        this.toolChains = toolChains;
        this.settings = settings;
        this.fileExtensions = fileExtensions;
        this.buildSystems = buildSystems;
        this.apiVersion = apiVersion;
    }

    @Override
    public int getApiVersion() {
        return apiVersion;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public Collection<File> getBuildFiles() {
        return buildFiles;
    }

    @Override
    @NonNull
    public Collection<NativeArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    @NonNull
    public Collection<NativeToolchain> getToolChains() {
        return toolChains;
    }

    @Override
    @NonNull
    public Collection<NativeSettings> getSettings() {
        return settings;
    }

    @Override
    @NonNull
    public Map<String, String> getFileExtensions() {
        return fileExtensions;
    }

    @Override
    @NonNull
    public Collection<String> getBuildSystems() {
        return buildSystems;
    }

    @NonNull
    @Override
    public String toString() {
        return "NativeAndroidProjectImpl{name='" + name + "'}";
    }
}
