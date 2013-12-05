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

package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.tasks.DependencyBasedCompileTask
import com.android.builder.compiling.DependencyFileProcessor
import com.google.common.collect.Lists
import org.gradle.api.tasks.InputFiles

/**
 * Task to compile aidl files. Supports incremental update.
 */
public class AidlCompile extends DependencyBasedCompileTask {

    // ----- PRIVATE TASK API -----

    @InputFiles
    List<File> sourceDirs

    @InputFiles
    List<File> importDirs

    @Override
    protected boolean isIncremental() {
        return true
    }

    @Override
    protected boolean supportsParallelization() {
        return true
    }

    @Override
    protected void compileAllFiles(DependencyFileProcessor dependencyFileProcessor) {
        getBuilder().compileAllAidlFiles(
                getSourceDirs(),
                getSourceOutputDir(),
                getImportDirs(),
                dependencyFileProcessor)
    }

    @Override
    protected Object incrementalSetup() {
        List<File> fullImportDir = Lists.newArrayList()
        fullImportDir.addAll(getImportDirs())
        fullImportDir.addAll(getSourceDirs())

        return fullImportDir
    }

    @Override
    protected void compileSingleFile(@NonNull File file,
                                     @Nullable Object data,
                                     @NonNull DependencyFileProcessor dependencyFileProcessor) {
        getBuilder().compileAidlFile(
                file,
                getSourceOutputDir(),
                (List<File>)data,
                dependencyFileProcessor)
    }
}
