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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.resources.Density;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {


    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * if there are pure splits, use the split-select tool otherwise revert to store logic.
     *
     * @param processExecutor an executor to execute native processes.
     * @param splitSelectExe the split select tool optionally.
     * @param deviceConfigProvider the device configuration.
     * @param outputs the list of variant outputs for find the best matching one.
     * @param variantAbiFilters the supported ABIs.
     * @return the list of APK files to install.
     * @throws ProcessException
     */
    @NonNull
    public static List<File> computeBestOutput(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull List<? extends VariantOutput> outputs,
            @Nullable Set<String> variantAbiFilters) throws ProcessException {

        // build the list of APKs.
        List<String> splitApksPath = new ArrayList<String>();
        OutputFile mainApk = null;
        for (VariantOutput output : outputs) {
            for (OutputFile outputFile : output.getOutputs()) {
                if (!outputFile.getOutputFile().getAbsolutePath().equals(
                        output.getMainOutputFile().getOutputFile().getAbsolutePath())) {

                    splitApksPath.add(outputFile.getOutputFile().getAbsolutePath());
                }
            }
            mainApk = output.getMainOutputFile();
        }

        List<File> apkFiles = new ArrayList<File>();
        if (splitSelectExe == null && !splitApksPath.isEmpty()) {
            throw new RuntimeException(
                    "Pure splits installation requires build tools 22 or above");
        }
        if (mainApk == null) {
            throw new RuntimeException(
                    "Cannot retrieve the main APK from variant outputs");
        }
        if (!splitApksPath.isEmpty()) {

            Set<String> resultApksPath = new HashSet<String>();
            for (String abi : deviceConfigProvider.getAbis()) {
                resultApksPath.addAll(SplitSelectTool.splitSelect(
                        processExecutor,
                        splitSelectExe,
                        deviceConfigProvider.getConfigFor(abi),
                        mainApk.getOutputFile().getAbsolutePath(),
                        splitApksPath));
            }
            for (String resultApkPath : resultApksPath) {
                apkFiles.add(new File(resultApkPath));
            }
            // and add back the main APK.
            apkFiles.add(mainApk.getOutputFile());
        } else {
            // now look for a matching output file
            List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                    outputs,
                    variantAbiFilters,
                    deviceConfigProvider.getDensity(),
                    deviceConfigProvider.getAbis());
            for (OutputFile outputFile : outputFiles) {
                apkFiles.add(outputFile.getOutputFile());
            }
        }
        return apkFiles;
    }

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * This uses the same logic as the store, using two passes:
     * First, find all the compatible outputs.
     * Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *                          of the outputs, if there is a single output with no abi filters.
     *                          If the list is null, then the variant does not restrict ABI
     *                          packaging.
     * @param deviceDensity the density of the device.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    @NonNull
    static List<OutputFile> computeBestOutput(
            @NonNull List<? extends VariantOutput> outputs,
            @Nullable Set<String> variantAbiFilters,
            int deviceDensity,
            @NonNull List<String> deviceAbis) {
        Density densityEnum = Density.getEnum(deviceDensity);

        String densityValue;
        if (densityEnum == null) {
            densityValue = null;
        } else {
            densityValue = densityEnum.getResourceValue();
        }

        // gather all compatible matches.
        Set<VariantOutput> matches = new HashSet<VariantOutput>();

        // find a matching output.
        for (VariantOutput variantOutput : outputs) {
            for (OutputFile output : variantOutput.getOutputs()) {
                String densityFilter = getFilter(output, OutputFile.DENSITY);
                String abiFilter = getFilter(output, OutputFile.ABI);

                if (densityFilter != null && !densityFilter.equals(densityValue)) {
                    continue;
                }

                if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                    continue;
                }
                // variantOutput can be added several times to matches.
                matches.add(variantOutput);
            }
        }

        if (matches.isEmpty()) {
            return ImmutableList.of();
        }

        VariantOutput match = Collections.max(matches, new Comparator<VariantOutput>() {
            @Override
            public int compare(VariantOutput splitOutput, VariantOutput splitOutput2) {
                return splitOutput.getVersionCode() - splitOutput2.getVersionCode();
            }
        });

        OutputFile mainOutputFile = match.getMainOutputFile();
        return isMainApkCompatibleWithDevice(mainOutputFile, variantAbiFilters, deviceAbis)
                ? ImmutableList.of(mainOutputFile)
                : ImmutableList.<OutputFile>of();
    }

    private static boolean isMainApkCompatibleWithDevice(
            OutputFile mainOutputFile,
            Set<String> variantAbiFilters,
            List<String> deviceAbis) {
        // so far, we are not dealing with the pure split files...
        if (getFilter(mainOutputFile, OutputFile.ABI) == null && variantAbiFilters != null) {
            // if we have a match that has no abi filter, and we have variant-level filters, then
            // we need to make sure that the variant filters are compatible with the device abis.
            for (String abi : deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Nullable
    private static String getFilter(@NonNull OutputFile outputFile, @NonNull String filterType) {
        for (FilterData filterData : outputFile.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }
}
