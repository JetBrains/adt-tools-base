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

import com.android.build.gradle.internal.tasks.NdkTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to compile Renderscript files. Supports incremental update.
 */
public class RenderscriptCompile extends NdkTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File sourceOutputDir

    @OutputDirectory
    File resOutputDir

    @OutputDirectory
    File objOutputDir

    @OutputDirectory
    File libOutputDir


    // ----- PRIVATE TASK API -----
    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    @InputFiles
    List<File> sourceDirs

    @InputFiles
    List<File> importDirs

    @Input
    Integer targetApi

    @Input
    boolean supportMode

    @Input
    int optimLevel

    @Input
    boolean debugBuild

    @Input
    boolean ndkMode

    @TaskAction
    void taskAction() {
        // this is full run (always), clean the previous outputs
        File sourceDestDir = getSourceOutputDir()
        emptyFolder(sourceDestDir)

        File resDestDir = getResOutputDir()
        emptyFolder(resDestDir)

        File objDestDir = getObjOutputDir()
        emptyFolder(objDestDir)

        File libDestDir = getLibOutputDir()
        emptyFolder(libDestDir)

        // get the import folders. If the .rsh files are not directly under the import folders,
        // we need to get the leaf folders, as this is what llvm-rs-cc expects.
        List<File> importFolders = getBuilder().getLeafFolders("rsh",
                getImportDirs(), getSourceDirs())

        getBuilder().compileAllRenderscriptFiles(
                getSourceDirs(),
                importFolders,
                sourceDestDir,
                resDestDir,
                objDestDir,
                libDestDir,
                getTargetApi(),
                getDebugBuild(),
                getOptimLevel(),
                getNdkMode(),
                getSupportMode(),
                getNdkConfig()?.abiFilters)
    }
}
