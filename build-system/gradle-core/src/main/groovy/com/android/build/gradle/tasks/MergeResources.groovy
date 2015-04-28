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
import com.android.build.gradle.internal.scope.ConventionMappingHelper
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.VariantType
import com.android.builder.png.QueuedCruncher
import com.android.ide.common.internal.PngCruncher
import com.android.ide.common.res2.FileStatus
import com.android.ide.common.res2.FileValidity
import com.android.ide.common.res2.MergedResourceWriter
import com.android.ide.common.res2.MergingException
import com.android.ide.common.res2.ResourceMerger
import com.android.ide.common.res2.ResourceSet
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.repository.FullRevision
import com.google.common.collect.Lists
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.OutputFile

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES

@ParallelizableTask
public class MergeResources extends IncrementalTask {
    public static final FullRevision NORMALIZE_RESOURCES_BUILD_TOOLS = new FullRevision(21, 0, 0)

    // ----- PUBLIC TASK API -----

    /** Directory to write the merged resources to */
    @OutputDirectory
    File outputDir

    /** Optional file to write any publicly imported resource types and names to */
    @Optional
    @OutputFile
    File publicFile

    // ----- PRIVATE TASK API -----

    // fake input to detect changes. Not actually used by the task
    @InputFiles
    Iterable<File> getRawInputFolders() {
        return flattenSourceSets(getInputResourceSets())
    }

    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    @Input
    boolean process9Patch

    @Input
    boolean crunchPng

    @Input
    boolean useNewCruncher;

    @Input
    boolean insertSourceMarkers = true

    @Input
    boolean normalizeResources

    // actual inputs
    List<ResourceSet> inputResourceSets

    private final FileValidity<ResourceSet> fileValidity = new FileValidity<ResourceSet>();

    @Override
    protected boolean isIncremental() {
        return true
    }

    private PngCruncher getCruncher() {
        if (getUseNewCruncher()) {
            if (builder.getTargetInfo().buildTools.getRevision().getMajor() >= 22) {
                return QueuedCruncher.Builder.INSTANCE.newCruncher(
                        builder.getTargetInfo().buildTools.getPath(
                                BuildToolInfo.PathId.AAPT), builder.getLogger())
            }
            logger.info("New PNG cruncher will be enabled with build tools 22 and above.")
        }
        return builder.aaptCruncher;
    }

    @Override
    protected void doFullTaskAction() {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir()
        emptyFolder(destinationDir)

        List<ResourceSet> resourceSets = getInputResourceSets()

        // create a new merger and populate it with the sets.
        ResourceMerger merger = new ResourceMerger()

        try {
            for (ResourceSet resourceSet : resourceSets) {
                resourceSet.setNormalizeResources(normalizeResources)
                // set needs to be loaded.
                resourceSet.loadFromFiles(getILogger())
                merger.addDataSet(resourceSet)
            }

            // get the merged set and write it down.
            MergedResourceWriter writer = new MergedResourceWriter(
                    destinationDir, getCruncher(),
                    getCrunchPng(), getProcess9Patch(), getPublicFile())
            writer.setInsertSourceMarkers(getInsertSourceMarkers())

            merger.mergeData(writer, false /*doCleanUp*/)

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer)
        } catch (MergingException e) {
            println e.getMessage()
            merger.cleanBlob(getIncrementalFolder())
            throw new ResourceException(e.getMessage(), e)
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        // create a merger and load the known state.
        ResourceMerger merger = new ResourceMerger()
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction()
                return
            }

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            List<ResourceSet> resourceSets = getInputResourceSets()
            for (ResourceSet resourceSet : resourceSets) {
                resourceSet.setNormalizeResources(normalizeResources)
            }

            if (!merger.checkValidUpdate(resourceSets)) {
                project.logger.info("Changed Resource sets: full task run!")
                doFullTaskAction()
                return
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey()

                merger.findDataSetContaining(changedFile, fileValidity)
                if (fileValidity.status == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction()
                    return
                } else if (fileValidity.status == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.dataSet.updateWith(
                            fileValidity.sourceFile, changedFile, entry.getValue(), getILogger())) {
                        project.logger.info(
                                String.format("Failed to process %s event! Full task run",
                                        entry.getValue()))
                        doFullTaskAction()
                        return
                    }
                }
            }

            MergedResourceWriter writer = new MergedResourceWriter(
                    getOutputDir(), getCruncher(),
                    getCrunchPng(), getProcess9Patch(), getPublicFile())
            writer.setInsertSourceMarkers(getInsertSourceMarkers())
            merger.mergeData(writer, false /*doCleanUp*/)
            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer)
        } catch (MergingException e) {
            println e.getMessage()
            merger.cleanBlob(getIncrementalFolder())
            throw new ResourceException(e.getMessage(), e)
        }
    }

    public static class ConfigAction implements TaskConfigAction<MergeResources> {

        @NonNull
        VariantScope scope
        @NonNull
        String taskNamePrefix
        @NonNull
        File outputLocation;
        boolean includeDependencies;
        boolean process9Patch;

        ConfigAction(VariantScope scope, String taskNamePrefix, File outputLocation,
                boolean includeDependencies, boolean process9Patch) {
            this.scope = scope
            this.taskNamePrefix = taskNamePrefix
            this.outputLocation = outputLocation
            this.includeDependencies = includeDependencies
            this.process9Patch = process9Patch

            scope.setMergeResourceOutputDir(outputLocation)
        }

        @Override
        String getName() {
            return scope.getTaskName(taskNamePrefix, "Resources")
        }

        @Override
        Class<MergeResources> getType() {
            return MergeResources
        }

        @Override
        void execute(MergeResources mergeResourcesTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.variantData

            mergeResourcesTask.androidBuilder = scope.globalScope.androidBuilder
            mergeResourcesTask.incrementalFolder = new File(
                    "$scope.globalScope.buildDir/${FD_INTERMEDIATES}/incremental/" +
                            "${taskNamePrefix}Resources/${variantData.variantConfiguration.dirName}")

            mergeResourcesTask.process9Patch = process9Patch
            mergeResourcesTask.crunchPng = scope.globalScope.extension.aaptOptions.getCruncherEnabled()
            mergeResourcesTask.normalizeResources =
                    scope.globalScope.extension.buildToolsRevision < NORMALIZE_RESOURCES_BUILD_TOOLS

            ConventionMappingHelper.map(mergeResourcesTask, "useNewCruncher") { scope.globalScope.getExtension().aaptOptions.useNewCruncher }

            ConventionMappingHelper.map(mergeResourcesTask, "inputResourceSets") {
                List<File> generatedResFolders = Lists.newArrayList(
                        scope.getRenderscriptResOutputDir(),
                        scope.getGeneratedResOutputDir())
                if (variantData.extraGeneratedResFolders != null) {
                    generatedResFolders += variantData.extraGeneratedResFolders
                }
                if (variantData.generateApkDataTask != null &&
                        variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
                    generatedResFolders.add(variantData.generateApkDataTask.getResOutputDir())
                }
                variantData.variantConfiguration.getResourceSets(generatedResFolders,
                        includeDependencies)
            }

            ConventionMappingHelper.map(mergeResourcesTask, "outputDir") {
                outputLocation ?: scope.getDefaultMergeResourcesOutputDir()
            }
            variantData.mergeResourcesTask = mergeResourcesTask
        }
    }
}
