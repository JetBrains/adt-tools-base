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
import com.android.build.gradle.internal.dsl.DexOptionsImpl
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.ide.common.res2.FileStatus
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile

public class Dex extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @OutputFile
    File outputFile

    // ----- PRIVATE TASK API -----

    @InputFiles
    Iterable<File> inputFiles

    @InputFiles
    Iterable<File> preDexedLibraries

    @Nested
    DexOptionsImpl dexOptions

    @Override
    protected void doFullTaskAction() {
        getBuilder().convertByteCode(
                getInputFiles(),
                getPreDexedLibraries(),
                getOutputFile(),
                getDexOptions(),
                false)
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        getBuilder().convertByteCode(
                getInputFiles(),
                getPreDexedLibraries(),
                getOutputFile(),
                getDexOptions(),
                true)
    }

    @Override
    protected boolean isIncremental() {
        return dexOptions.incremental
    }
}
