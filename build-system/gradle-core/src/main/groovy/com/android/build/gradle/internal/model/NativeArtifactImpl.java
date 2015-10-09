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
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * Implementation of {@link NativeArtifact}.
 */
public class NativeArtifactImpl implements NativeArtifact, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final String toolChain;
    @NonNull
    private final Collection<NativeFolder> sourceFolders;
    @NonNull
    private final Collection<NativeFile> sourceFiles;
    @NonNull
    private final File getOutputFile;

    public NativeArtifactImpl(
            @NonNull String name,
            @NonNull String toolChain,
            @NonNull Collection<NativeFolder> sourceFolders,
            @NonNull Collection<NativeFile> sourceFiles,
            @NonNull File getOutputFile) {
        this.name = name;
        this.toolChain = toolChain;
        this.sourceFolders = sourceFolders;
        this.sourceFiles = sourceFiles;
        this.getOutputFile = getOutputFile;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String getToolChain() {
        return toolChain;
    }

    @Override
    @NonNull
    public Collection<NativeFolder> getSourceFolders() {
        return sourceFolders;
    }

    @Override
    @NonNull
    public Collection<NativeFile> getSourceFiles() {
        return sourceFiles;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return getOutputFile;
    }
}
