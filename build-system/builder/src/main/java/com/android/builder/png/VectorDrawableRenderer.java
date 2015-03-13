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
import com.android.resources.Density;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Generates PNG images from VectorDrawable files.
 */
public class VectorDrawableRenderer {

    /** Projects with minSdk set to this or higher don't need to generate PNGs. */
    public static final int MIN_SDK_WITH_VECTOR_SUPPORT = 21;

    public void createPngFiles(
            @NonNull File inputXmlFile,
            @NonNull File outputResDirectory,
            @NonNull Collection<Density> densities) throws IOException {
        checkArgument(inputXmlFile.exists());
        checkArgument(outputResDirectory.exists());

        for (Density density : densities) {
            // Sketch implementation.

            // TODO: add the density to all other qualifiers of the original file.
            File directory = new File(outputResDirectory, "drawable-" + density.getResourceValue());
            directory.mkdir();
            File pngFile = new File(directory, inputXmlFile.getName().replace(".xml", ".png"));

            pngFile.createNewFile(); // Create an empty file for now.
        }

        File v21Dir = new File(outputResDirectory, "drawable-v21");
        v21Dir.mkdirs();
        // TODO: make all the drawable-hdpi-v21 aliases.
        Files.move(inputXmlFile, new File(v21Dir, inputXmlFile.getName()));
    }

    public boolean isVectorDrawable(File xmlFile) {
        // TODO: parse the root element of the file to check.
        return true;
    }
}
