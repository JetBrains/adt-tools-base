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

package com.android.build.gradle.tasks;

import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.PreprocessDataSet;
import com.android.ide.common.res2.PreprocessResourcesMerger;
import com.android.ide.common.res2.PreprocessResourcesWriter;
import com.android.resources.Density;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Generates PNGs from Android vector drawable files.
 */
public class PreprocessResourcesTask extends IncrementalTask {
    public static final int MIN_SDK = VectorDrawableRenderer.MIN_SDK_WITH_VECTOR_SUPPORT;

    private final VectorDrawableRenderer renderer = new VectorDrawableRenderer();
    private File outputResDirectory;
    private File generatedResDirectory;
    private File mergedResDirectory;
    private Collection<Density> densitiesToGenerate;
    private String variantName;

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        // TODO: Check for changes in the densities.
        PreprocessResourcesMerger merger = new PreprocessResourcesMerger();
        try {
            Files.touch(new File(getIncrementalFolder(), "build_was_incremental"));

            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            SetMultimap<File, File> generatedFiles = HashMultimap.create();
            for (PreprocessDataSet dataSet : merger.getDataSets()) {
                dataSet.setGeneratedResDirectory(getGeneratedResDirectory());
                dataSet.setMergedResDirectory(getMergedResDirectory());
                dataSet.setGeneratedFiles(generatedFiles);
            }

            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                switch (entry.getValue()) {
                    case CHANGED: // Fall through.
                    case NEW:
                        handleFile(
                                entry.getKey(),
                                entry.getValue(),
                                merger.getMergedDataSet(),
                                merger.getGeneratedDataSet(),
                                generatedFiles);
                        break;
                    case REMOVED:
                        merger.getMergedDataSet().updateWith(
                                getMergedResDirectory(),
                                entry.getKey(),
                                FileStatus.REMOVED,
                                getILogger());
                        merger.getGeneratedDataSet().updateWith(
                                getMergedResDirectory(),
                                entry.getKey(),
                                FileStatus.REMOVED,
                                getILogger());
                       break;
                }
            }

            finalizeMerge(merger);
        } catch (MergingException e) {
            merger.cleanBlob(getIncrementalFolder());
            throw new RuntimeException(e);
        } catch (IOException e) {
            merger.cleanBlob(getIncrementalFolder());
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doFullTaskAction() {
        // Maps input files to all files generated from them. This is used by the generated data set
        // when it needs to create all data items that come from a given data file.
        SetMultimap<File, File> generatedFiles = HashMultimap.create();
        emptyFolder(getOutputResDirectory());
        emptyFolder(getGeneratedResDirectory());
        emptyFolder(getIncrementalFolder());

        PreprocessDataSet mergedSet = new PreprocessDataSet(
                getVariantName(),
                PreprocessDataSet.ResourcesDirectory.MERGED);
        mergedSet.addSource(getMergedResDirectory());
        mergedSet.setMergedResDirectory(getMergedResDirectory());

        PreprocessDataSet generatedSet = new PreprocessDataSet(
                getVariantName(),
                PreprocessDataSet.ResourcesDirectory.GENERATED);
        generatedSet.addSource(getMergedResDirectory());
        generatedSet.setGeneratedResDirectory(getGeneratedResDirectory());

        // This map will be updated as we generate more files.
        generatedSet.setGeneratedFiles(generatedFiles);

        try {
            for (File resourceFile : getProject().fileTree(getMergedResDirectory())) {
                handleFile(resourceFile, FileStatus.NEW, mergedSet, generatedSet, generatedFiles);
            }

            PreprocessResourcesMerger merger = new PreprocessResourcesMerger();
            // Files from the merged directory take precedence.
            merger.addDataSet(generatedSet);
            merger.addDataSet(mergedSet);

            finalizeMerge(merger);
        } catch (MergingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void finalizeMerge(PreprocessResourcesMerger merger)
            throws MergingException {
        PreprocessResourcesWriter writer = new PreprocessResourcesWriter(getOutputResDirectory());
        merger.mergeData(writer, true);
        merger.writeBlobTo(getIncrementalFolder(), writer);
    }

    private void handleFile(
            File resourceFile,
            FileStatus fileStatus,
            PreprocessDataSet mergedSet,
            PreprocessDataSet generatedSet,
            SetMultimap<File, File> generatedFiles) throws IOException, MergingException {
        if (renderer.isVectorDrawable(resourceFile)) {
            Collection<File> newFiles = renderer.createPngFiles(
                    resourceFile,
                    getGeneratedResDirectory(),
                    getDensitiesToGenerate());

            generatedFiles.putAll(resourceFile, newFiles);

            generatedSet.updateWith(
                    getMergedResDirectory(),
                    resourceFile,
                    fileStatus,
                    getILogger());
        } else {
            mergedSet.updateWith(
                    getMergedResDirectory(),
                    resourceFile,
                    fileStatus,
                    getILogger());
        }
    }

    /**
     * Directory in which to put generated files. They will be then copied to the final destination
     * if this would not overwrite anything.
     *
     * @see #getOutputResDirectory()
     */
    @OutputDirectory
    public File getGeneratedResDirectory() {
        return generatedResDirectory;
    }

    public void setGeneratedResDirectory(File generatedResDirectory) {
        this.generatedResDirectory = generatedResDirectory;
    }

    /**
     * "Input" resource directory, with all resources for the current variant merged.
     */
    @InputDirectory
    public File getMergedResDirectory() {
        return mergedResDirectory;
    }

    public void setMergedResDirectory(File mergedResDirectory) {
        this.mergedResDirectory = mergedResDirectory;
    }

    /**
     * Resources directory that will be passed to aapt.
     */
    @OutputDirectory
    public File getOutputResDirectory() {
        return outputResDirectory;
    }

    public void setOutputResDirectory(File outputResDirectory) {
        this.outputResDirectory = outputResDirectory;
    }

    public Collection<Density> getDensitiesToGenerate() {
        return densitiesToGenerate;
    }

    public void setDensitiesToGenerate(
            Collection<Density> densitiesToGenerate) {
        this.densitiesToGenerate = densitiesToGenerate;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }
}
