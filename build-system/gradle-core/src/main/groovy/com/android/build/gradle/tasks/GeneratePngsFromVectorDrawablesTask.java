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

import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.resources.Density;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Generates PNGs from an Android vector drawables.
 */
// TODO: Make it incremental, handle deleted resources.
public class GeneratePngsFromVectorDrawablesTask extends BaseTask {
    private final VectorDrawableRenderer renderer = new VectorDrawableRenderer();
    private FileCollection xmlFiles;
    private File outputResDirectory;
    private Collection<Density> densitiesToGenerate;

    /**
     * XML files under in a drawable res folder, potential VectorDrawable resources.
     */
    @Input
    public FileCollection getXmlFiles() {
        return xmlFiles;
    }

    public void setXmlFiles(FileCollection xmlFiles) {
        this.xmlFiles = xmlFiles;
    }

    /**
     * res directory where the generated PNGs should be put.
     */
    @OutputDirectory
    public File getOutputResDirectory() {
        return outputResDirectory;
    }

    public void setOutputResDirectory(File outputResDirectory) {
        this.outputResDirectory = outputResDirectory;
    }

    @Input
    public Collection<Density> getDensitiesToGenerate() {
        return densitiesToGenerate;
    }

    public void setDensitiesToGenerate(
            Collection<Density> densitiesToGenerate) {
        this.densitiesToGenerate = densitiesToGenerate;
    }

    @TaskAction
    public void generatePngs() throws IOException {
        for (File xmlFile : getXmlFiles()) {
            if (renderer.isVectorDrawable(xmlFile)) {
                renderer.createPngFiles(xmlFile, getOutputResDirectory(), getDensitiesToGenerate());
            }
        }
    }

}
