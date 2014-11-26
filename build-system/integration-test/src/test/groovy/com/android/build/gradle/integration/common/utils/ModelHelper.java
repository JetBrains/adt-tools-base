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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;

import junit.framework.Assert;

import java.io.File;
import java.util.Collection;

/**
 * Utility helper to help read/test the AndroidProject Model.
 */
public class ModelHelper {

    /**
     * Returns a Variant object from a given name
     * @param variants the list of variants
     * @param variantName the name of the variant to return
     * @return the matching variant or null if not found
     */
    @Nullable
    public static Variant findVariantByName(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName) {
        for (Variant item : variants) {
            if (variantName.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    /**
     * Returns the APK file for a single-output variant.
     * @param variants the list of variants
     * @param variantName the name of the variant to return
     * @return the output file, always, or assert before.
     */
    @NonNull
    public static File findOutputFileByVariantName(
            @NonNull Collection<Variant> variants,
            @NonNull String variantName) {

        Variant variant = findVariantByName(variants, variantName);
        assertNotNull(
                "variant '" + variantName + "' null-check",
                variant);

        AndroidArtifact artifact = variant.getMainArtifact();
        assertNotNull(
                "variantName '" + variantName + "' main artifact null-check",
                artifact);

        Collection<AndroidArtifactOutput> variantOutputs = artifact.getOutputs();
        assertNotNull(
                "variantName '" + variantName + "' outputs null-check",
                variantOutputs);
        // we only support single output artifact in this helper method.
        Assert.assertEquals(
                "variantName '" + variantName + "' outputs size check",
                1,
                variantOutputs.size());

        AndroidArtifactOutput output = variantOutputs.iterator().next();
        assertNotNull(
                "variantName '" + variantName + "' single output null-check",
                output);

        // we only support single outputFile in this helper method.
        // we're not going to use this, this is a state check only.
        Collection<? extends OutputFile> outputFiles = output.getOutputs();
        assertNotNull(
                "variantName '" + variantName + "' outputFiles null-check",
                outputFiles);
        Assert.assertEquals(
                "variantName '" + variantName + "' outputFiles size check",
                1,
                outputFiles.size());

        // get the main output file
        OutputFile mainOutputFile = output.getMainOutputFile();
        assertNotNull(
                "variantName '" + variantName + "' mainOutputFile null-check",
                mainOutputFile);

        return mainOutputFile.getOutputFile();
    }
}
