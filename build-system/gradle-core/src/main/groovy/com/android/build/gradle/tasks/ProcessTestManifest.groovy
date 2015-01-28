/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.gradle.internal.dependency.ManifestDependencyImpl
import com.google.common.collect.Lists
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.ParallelizableTask

/**
 * A task that processes the manifest
 */
@ParallelizableTask
public class ProcessTestManifest extends ManifestProcessorTask {

    @InputFile
    @Optional
    File testManifestFile

    File tmpDir

    // ----- PRIVATE TASK API -----

    @Input
    String testApplicationId

    @Input @Optional
    String minSdkVersion

    @Input @Optional
    String targetSdkVersion

    @Input
    String testedApplicationId

    @Input
    String instrumentationRunner

    @Input
    Boolean handleProfiling;

    @Input
    Boolean functionalTest;

    List<ManifestDependencyImpl> libraries

    /*
     * since libraries above can't return it's input files (@Nested doesn't
     * work on lists), so do a method that will gather them and return them.
     */
    @InputFiles
    List<File> getLibraryManifests() {
        List<ManifestDependencyImpl> libs = getLibraries()
        if (libs == null || libs.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> files = Lists.newArrayListWithCapacity(libs.size() * 2)
        for (ManifestDependencyImpl mdi : libs) {
            files.addAll(mdi.getAllManifests())
        }

        return files;
    }

    @Override
    protected void doFullTaskAction() {
        getBuilder().processTestManifest(
                getTestApplicationId(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getTestedApplicationId(),
                getInstrumentationRunner(),
                getHandleProfiling(),
                getFunctionalTest(),
                getTestManifestFile(),
                getLibraries(),
                getManifestOutputFile(),
                getTmpDir())
    }
}
