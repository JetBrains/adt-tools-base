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

package com.android.builder.png;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Generates PNG images (and XML copies) from VectorDrawable files.
 */
public class VectorDrawableRenderer {

    /** Projects with minSdk set to this or higher don't need to generate PNGs. */
    public static final int MIN_SDK_WITH_VECTOR_SUPPORT = 21;

    public Collection<File> createPngFiles(
            @NonNull File inputXmlFile,
            @NonNull File outputResDirectory,
            @NonNull Collection<Density> densities) throws IOException {
        checkArgument(inputXmlFile.exists());
        checkArgument(outputResDirectory.exists());
        checkArgument(
                isInDrawable(inputXmlFile),
                "XML file is not in a 'drawable-*' folder, [%s].",
                inputXmlFile);

        FolderConfiguration originalConfiguration = getFolderConfiguration(inputXmlFile);

        // Create all the PNG files and duplicate the XML into folders with the version qualifier.
        Collection<File> createdFiles = Lists.newArrayList();
        for (Density density : densities) {
            FolderConfiguration newConfiguration = FolderConfiguration.copyOf(originalConfiguration);
            newConfiguration.setDensityQualifier(new DensityQualifier(density));

            File directory = new File(
                    outputResDirectory,
                    newConfiguration.getFolderName(ResourceFolderType.DRAWABLE));
            File pngFile = new File(
                    directory,
                    inputXmlFile.getName().replace(".xml", ".png"));

            Files.createParentDirs(pngFile);
            Files.write(
                    String.format(
                            "%s in %s, %s%n",
                            inputXmlFile.getName(),
                            density.getResourceValue(),
                            // For testing, make sure different inputs produce different outputs.
                            FileUtils.sha1(inputXmlFile)),
                    pngFile,
                    Charsets.UTF_8);
            createdFiles.add(pngFile);

            newConfiguration.setVersionQualifier(new VersionQualifier(MIN_SDK_WITH_VECTOR_SUPPORT));
            File xmlCopy = copyOriginalXml(inputXmlFile, outputResDirectory, newConfiguration);
            createdFiles.add(xmlCopy);
        }

        return createdFiles;
    }

    @NonNull
    private File copyOriginalXml(
            @NonNull File inputXmlFile,
            @NonNull File outputResDirectory,
            FolderConfiguration newConfiguration) throws IOException {
        File destination = new File(
                outputResDirectory,
                newConfiguration.getFolderName(ResourceFolderType.DRAWABLE));
        File copy = new File(destination, inputXmlFile.getName());

        Files.createParentDirs(copy);
        Files.copy(inputXmlFile, copy);
        return copy;
    }

    @NonNull
    private FolderConfiguration getFolderConfiguration(@NonNull File inputXmlFile) {
        String parentName = inputXmlFile.getParentFile().getName();
        FolderConfiguration originalConfiguration =
                FolderConfiguration.getConfigForFolder(parentName);
        checkArgument(
                originalConfiguration != null,
                "Invalid resource folder name [%s].",
                parentName);
        return originalConfiguration;
    }

    private boolean isInDrawable(@NonNull File inputXmlFile) {
        ResourceFolderType folderType =
                ResourceFolderType.getFolderType(inputXmlFile.getParentFile().getName());

        return folderType == ResourceFolderType.DRAWABLE;
    }

    public boolean needsPreprocessing(File resourceFile) {
        // TODO: parse the root element of the file to check.
        return resourceFile.getPath().endsWith(".xml")
                && isInDrawable(resourceFile)
                && getEffectiveVersion(resourceFile) < MIN_SDK_WITH_VECTOR_SUPPORT;
    }

    private int getEffectiveVersion(File resourceFile) {
        FolderConfiguration configuration = getFolderConfiguration(resourceFile);
        configuration.createDefault();
        // Because of the above, the will be no NPE here.
        //noinspection ConstantConditions
        return configuration.getVersionQualifier().getVersion();
    }
}
