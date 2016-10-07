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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Copy;
import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;
import java.util.Collection;

/**
 * Merge native libraries and their dependencies into a single folder.
 */
public class MergeNativeLibrariesConfigAction implements Action<Copy> {

    @NonNull
    private final NativeBinarySpec binary;
    @NonNull
    private final File inputFolder;
    @NonNull
    private final Collection<File> inputFiles;
    @NonNull
    private final Collection<FileCollection> inputFileCollections;
    @NonNull
    private final File buildDir;

    public MergeNativeLibrariesConfigAction(
            @NonNull NativeBinarySpec binary,
            @NonNull File inputFolder,
            @NonNull Collection<File> inputFiles,
            @NonNull Collection<FileCollection> inputFileCollections,
            @NonNull File buildDir) {
        this.binary = binary;
        this.inputFolder = inputFolder;
        this.inputFiles = inputFiles;
        this.inputFileCollections = inputFileCollections;
        this.buildDir = buildDir;
    }

    @Override
    public void execute(@NonNull Copy task) {
        task.from(inputFolder);
        task.from(inputFiles);
        task.from(inputFileCollections);
        task.into(new File(
                buildDir,
                NdkNamingScheme.getOutputDirectoryName(binary)));
        task.dependsOn(binary);
    }
}
