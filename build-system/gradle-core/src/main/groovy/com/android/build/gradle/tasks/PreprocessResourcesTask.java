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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.res2.FileStatus;
import com.android.resources.Density;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Generates PNGs from Android vector drawable files.
 */
public class PreprocessResourcesTask extends IncrementalTask {
    public static final int MIN_SDK = VectorDrawableRenderer.MIN_SDK_WITH_VECTOR_SUPPORT;
    private static final Type TYPE_TOKEN = new TypeToken<Map<String, Collection<String>>>() {}.getType();

    private final VectorDrawableRenderer renderer = new VectorDrawableRenderer();
    private File outputResDirectory;
    private File generatedResDirectory;
    private File mergedResDirectory;
    private Collection<Density> densitiesToGenerate;

    @Override
    protected boolean isIncremental() {
        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        try {
            File incrementalMarker = new File(getIncrementalFolder(), "build_was_incremental");
            Files.touch(incrementalMarker);

            // TODO: store and check the set of densities.

            File stateFile = getStateFile();
            if (!stateFile.exists()) {
                doFullTaskAction();
            }

            SetMultimap<String, String> state = readState(stateFile);

            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                switch (entry.getValue()) {
                    case NEW:
                    case CHANGED:
                        getLogger().debug("Incremental change to {}.",
                                entry.getKey().getAbsolutePath());
                        handleFile(entry.getKey(), state);
                        break;
                    case REMOVED:
                        for (String path : state.get(entry.getKey().getAbsolutePath())) {
                            File file = new File(path);
                            getLogger().debug("Deleting {}.", file.getAbsolutePath());
                            file.delete();
                        }
                        state.removeAll(entry.getKey());
                        break;
                    default:
                        throw new RuntimeException("Unsupported operation " + entry.getValue());
                }
            }

            saveState(state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doFullTaskAction() {
        SetMultimap<String, String> state = HashMultimap.create();
        emptyFolder(getOutputResDirectory());
        emptyFolder(getGeneratedResDirectory());
        emptyFolder(getIncrementalFolder());

        try {
            for (File resourceFile : getProject().fileTree(getMergedResDirectory())) {
                handleFile(resourceFile, state);
            }
            saveState(state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static SetMultimap<String, String> readState(@NonNull File stateFile) throws IOException {
        String stateString = Files.toString(stateFile, Charsets.UTF_8);
        Map<String, Collection<String>> stateMap = new Gson().fromJson(stateString, TYPE_TOKEN);

        SetMultimap<String, String> state = HashMultimap.create();
        for (Map.Entry<String, Collection<String>> entry : stateMap.entrySet()) {
            state.putAll(entry.getKey(), entry.getValue());
        }
        return state;
    }

    private void saveState(@NonNull Multimap<String, String> state) throws IOException {
        File stateFile = getStateFile();
        Files.write(new Gson().toJson(state.asMap(), TYPE_TOKEN), stateFile, Charsets.UTF_8);
    }

    @NonNull
    private File getStateFile() {
        return new File(getIncrementalFolder(), "state.json");
    }


    private void handleFile(@NonNull File resourceFile, @NonNull SetMultimap<String, String> state)
            throws IOException {
        if (renderer.isVectorDrawable(resourceFile)) {
            getLogger().debug("Generating files for {}.", resourceFile.getAbsolutePath());
            Collection<File> generatedFiles = renderer.createPngFiles(
                    resourceFile,
                    getGeneratedResDirectory(),
                    getDensitiesToGenerate());

            for (File generatedFile : generatedFiles) {
                getLogger().debug("Copying generated file: {}.", generatedFile.getAbsolutePath());
                copyFile(generatedFile, resourceFile, getGeneratedResDirectory(), state);
            }
        } else {
            getLogger().debug("Copying as-is: {}", resourceFile.getAbsolutePath());
            copyFile(resourceFile, resourceFile, getMergedResDirectory(), state);
        }
    }

    private void copyFile(
            @NonNull File fileToUse,
            @NonNull File originalFile,
            @NonNull File resDir,
            @NonNull SetMultimap<String, String> state) throws IOException {
        checkNotNull(resDir);
        String relativePath =
                resDir.toURI().relativize(fileToUse.toURI()).getPath();

        File finalFile = new File(getOutputResDirectory(), relativePath);
        Files.createParentDirs(finalFile);
        Files.copy(fileToUse, finalFile);
        state.put(originalFile.getAbsolutePath(), finalFile.getAbsolutePath());
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

    @NonNull
    @Override
    public Logger getLogger() {
        return Logging.getLogger(getClass());
    }
}
