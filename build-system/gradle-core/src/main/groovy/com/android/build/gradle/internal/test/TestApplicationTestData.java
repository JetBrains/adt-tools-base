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

package com.android.build.gradle.internal.test;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.publishing.FilterDataPersistence;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.ApkInfoParser;
import com.android.builder.model.SourceProvider;
import com.android.builder.testing.TestData;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link TestData} for separate test modules.
 */
public class TestApplicationTestData extends  AbstractTestDataImpl {

    private final Configuration testedConfiguration;
    private final Configuration testedMetadata;
    private final AndroidBuilder androidBuilder;
    private final BaseVariantData testVariant;

    public TestApplicationTestData(
            BaseVariantData<? extends BaseVariantOutputData>  testVariantData,
            Configuration testedConfiguration,
            Configuration testedMetadata,
            AndroidBuilder androidBuilder) {
        super(testVariantData.getVariantConfiguration());
        this.testVariant = testVariantData;
        this.testedConfiguration = testedConfiguration;
        this.testedMetadata = testedMetadata;
        this.androidBuilder = androidBuilder;
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testVariant.getApplicationId();
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        ApkInfoParser.ApkInfo apkInfo = loadTestedApkInfo();
        return apkInfo.getPackageName();
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(int density, @Nullable String language,
            @Nullable String region, @NonNull List<String> abis) {
        // retrieve the apk from the dependency handler ?

        ImmutableList.Builder<File> testedApks = ImmutableList.builder();
        testedApks.addAll(testedConfiguration.getFiles());

        // TODO: handle split packages.
/**        List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                testedVariantData.getOutputs(),
                testedVariantData.getVariantConfiguration().getSupportedAbis(),
                density,
                language,
                region,
                abis);
        return*/
        return testedApks.build();
    }

    @NonNull
    @Override
    public File getTestApk() {
        return ((ApkVariantOutputData) testVariant.getOutputs().get(0))
                .getOutputs().get(0).getOutputFile();
    }

    @NonNull
    @Override
    public List<File> getTestDirectories() {
        // For now we check if there are any test sources. We could inspect the test classes and
        // apply JUnit logic to see if there's something to run, but that would not catch the case
        // where user makes a typo in a test name or forgets to inherit from a JUnit class
        GradleVariantConfiguration variantConfiguration = testVariant.getVariantConfiguration();
        ImmutableList.Builder<File> javaDirectories = ImmutableList.builder();
        for (SourceProvider sourceProvider : variantConfiguration.getSortedSourceProviders()) {
            javaDirectories.addAll(sourceProvider.getJavaDirectories());
        }
        return javaDirectories.build();
    }

    private ApkInfoParser.ApkInfo loadTestedApkInfo() {

        File aaptFile = new File(androidBuilder.getTargetInfo().getBuildTools()
                .getPath(BuildToolInfo.PathId.AAPT));
        ApkInfoParser apkInfoParser =
                new ApkInfoParser(aaptFile, androidBuilder.getProcessExecutor());
        try {
            return apkInfoParser.parseApk(getMainApk());
        } catch (ProcessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve the main APK from the list of APKs published by the tested configuration. There can
     * be multiple split APKs along the main APK returned by the configuration.
     *
     * @return the tested main APK.
     */
    @NonNull
    public File getMainApk() {
        Set<File> testedApkFiles = new HashSet<File>(testedConfiguration.getFiles());
        if (testedApkFiles.size() > 1) {
            // we have splits in the mix, find the right APK.
            File metadataFile = testedMetadata.getSingleFile();
            FilterDataPersistence persistence = new FilterDataPersistence();
            List<FilterDataPersistence.Record> filterDatas;
            try {
                filterDatas = persistence.load(new FileReader(metadataFile));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (FilterDataPersistence.Record filterData : filterDatas) {
                File splitFile = findSplitFile(testedApkFiles, filterData.splitFileName);
                if (splitFile != null) {
                    testedApkFiles.remove(splitFile);
                } else {
                    // this is an error, we cannot find a file which is in the variant metadata
                    throw new RuntimeException(
                            String.format("Internal Error : %1$s is not in the list of published files",
                                    filterData.splitFileName));
                }
            }
            // at this point, only the main APK file should remain.
            if (testedApkFiles.size() != 1) {
                // we still have published files we don't know about.
                throw new RuntimeException(
                        String.format("Internal Error : %1$s files are not in the variant metadata",
                                Joiner.on(",").join(testedApkFiles)));
            }
        }
        if (testedApkFiles.isEmpty()) {
            throw new RuntimeException("Cannot retrieve tested APKs");
        }
        return Iterables.getOnlyElement(testedApkFiles);
    }

    @Nullable
    private static File findSplitFile(Collection<File> splitFiles, String splitFileName) {
        for (File splitFile : splitFiles) {
            if (splitFile.getName().equals(splitFileName)) {
                return splitFile;
            }
        }
        return null;
    }
}
