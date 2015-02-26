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

package com.android.build.gradle.internal.test;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.model.ApiVersion;
import com.android.builder.testing.TestData;
import com.android.ide.common.build.SplitOutputMatcher;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Implementation of {@link TestData} on top of a {@link TestVariantData}
 */
public class TestDataImpl implements TestData {

    @NonNull
    private final TestVariantData testVariantData;

    @NonNull
    private final VariantConfiguration testVariantConfig;

    public TestDataImpl(
            @NonNull TestVariantData testVariantData) {
        this.testVariantData = testVariantData;
        this.testVariantConfig = testVariantData.getVariantConfiguration();
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testVariantData.getApplicationId();
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        return testVariantConfig.getTestedApplicationId();
    }

    @NonNull
    @Override
    public String getInstrumentationRunner() {
        return testVariantConfig.getInstrumentationRunner();
    }

    @NonNull
    @Override
    public Boolean getHandleProfiling() {
        return testVariantConfig.getHandleProfiling();
    }

    @NonNull
    @Override
    public Boolean getFunctionalTest() {
        return testVariantConfig.getFunctionalTest();
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return testVariantConfig.isTestCoverageEnabled();
    }

    @NonNull
    @Override
    public ApiVersion getMinSdkVersion() {
        return testVariantConfig.getMinSdkVersion();
    }

    @Override
    public boolean isLibrary() {
        TestedVariantData testedVariantData = testVariantData.getTestedVariantData();
        BaseVariantData<?> testedVariantData2 = (BaseVariantData) testedVariantData;
        return testedVariantData2.getVariantConfiguration().getType() == VariantType.LIBRARY;
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            int density,
            @Nullable String language,
            @Nullable String region,
            @NonNull List<String> abis) {
        TestedVariantData testedVariantData = testVariantData.getTestedVariantData();
        BaseVariantData<?> testedVariantData2 = (BaseVariantData) testedVariantData;

        List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                testedVariantData2.getOutputs(),
                testedVariantData2.getVariantConfiguration().getSupportedAbis(),
                density,
                language,
                region,
                abis);
        ImmutableList.Builder<File> apks = ImmutableList.builder();
        for (OutputFile outputFile : outputFiles) {
            apks.add(((ApkOutputFile) outputFile).getOutputFile());
        }
        return apks.build();
    }
}
