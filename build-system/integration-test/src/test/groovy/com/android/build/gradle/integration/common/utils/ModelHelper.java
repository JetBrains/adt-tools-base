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

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;

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
        assertEquals(
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
        assertEquals(
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

    public static void testDefaultSourceSets(
            @NonNull AndroidProject model,
            @NonNull File projectDir) {
        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        // test the main source provider
        new SourceProviderHelper(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();

        // test the main instrumentTest source provider
        SourceProviderContainer testSourceProviders = getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);
        assertNotNull("InstrumentTest source Providers null-check", testSourceProviders);

        new SourceProviderHelper(model.getName(), projectDir,
                ANDROID_TEST.getPrefix(), testSourceProviders.getSourceProvider())
                .test();

        // test the source provider for the build types
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());

        for (BuildTypeContainer btContainer : model.getBuildTypes()) {
            new SourceProviderHelper(
                    model.getName(),
                    projectDir,
                    btContainer.getBuildType().getName(),
                    btContainer.getSourceProvider())
                    .test();

            assertEquals(0, btContainer.getExtraSourceProviders().size());
        }
    }

    public static void compareDebugAndReleaseOutput(@NonNull AndroidProject model) {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // debug variant
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);

        // debug artifact
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainInfo);

        Collection<AndroidArtifactOutput> debugMainOutputs = debugMainInfo.getOutputs();
        assertNotNull("Debug main output null-check", debugMainOutputs);

        // release variant
        Variant releaseVariant = getVariant(variants, "release");
        assertNotNull("release Variant null-check", releaseVariant);

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact();
        assertNotNull("Release main info null-check", relMainInfo);

        Collection<AndroidArtifactOutput> relMainOutputs = relMainInfo.getOutputs();
        assertNotNull("Rel Main output null-check", relMainOutputs);

        File debugFile = debugMainOutputs.iterator().next().getMainOutputFile().getOutputFile();
        File releaseFile = relMainOutputs.iterator().next().getMainOutputFile().getOutputFile();

        assertFalse("debug: " + debugFile + " / release: " + releaseFile,
                debugFile.equals(releaseFile));
    }


    @Nullable
    public static Variant getVariant(
            @NonNull Collection<Variant> items,
            @NonNull String name) {
        for (Variant item : items) {
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static AndroidArtifact getAndroidArtifact(
            @NonNull Collection<AndroidArtifact> items,
            @NonNull String name) {
        for (AndroidArtifact item : items) {
            assertNotNull("AndroidArtifact list item null-check:" + name, item);
            assertNotNull("AndroidArtifact.getName() list item null-check: " + name, item.getName());
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static SigningConfig getSigningConfig(
            @NonNull Collection<SigningConfig> items,
            @NonNull String name) {
        for (SigningConfig item : items) {
            assertNotNull("SigningConfig list item null-check:" + name, item);
            assertNotNull("SigningConfig.getName() list item null-check: " + name, item.getName());
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static SourceProviderContainer getSourceProviderContainer(
            @NonNull Collection<SourceProviderContainer> items,
            @NonNull String name) {
        for (SourceProviderContainer item : items) {
            assertNotNull("SourceProviderContainer list item null-check:" + name, item);
            assertNotNull("SourceProviderContainer.getName() list item null-check: " + name, item.getArtifactName());
            if (name.equals(item.getArtifactName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static String getFilter(@NonNull OutputFile outputFile, @NonNull String filterType) {
        for (FilterData filterData : outputFile.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }

    @Nullable
    public static ArtifactMetaData getArtifactMetaData(
            @NonNull Collection<ArtifactMetaData> items,
            @NonNull String name) {
        for (ArtifactMetaData item : items) {
            assertNotNull("ArtifactMetaData list item null-check:" + name, item);
            assertNotNull("ArtifactMetaData.getName() list item null-check: " + name, item.getName());
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public static ProductFlavorContainer getProductFlavor(
            @NonNull Collection<ProductFlavorContainer> items,
            @NonNull String name) {
        for (ProductFlavorContainer item : items) {
            assertNotNull("ProductFlavorContainer list item null-check:" + name, item);
            assertNotNull("ProductFlavorContainer.getProductFlavor() list item null-check: " + name, item.getProductFlavor());
            assertNotNull("ProductFlavorContainer.getProductFlavor().getName() list item null-check: " + name, item.getProductFlavor().getName());
            if (name.equals(item.getProductFlavor().getName())) {
                return item;
            }
        }

        return null;
    }
}
