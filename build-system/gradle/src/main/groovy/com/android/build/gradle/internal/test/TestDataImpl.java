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
import com.android.build.SplitOutput;
import com.android.build.gradle.api.ApkOutput;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.ApiVersion;
import com.android.builder.testing.TestData;
import com.android.ide.common.build.SplitOutputMatcher;

import java.io.File;
import java.util.ArrayList;
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
        return testedVariantData2.getVariantConfiguration().getType() == VariantConfiguration.Type.LIBRARY;
    }

    @Nullable
    @Override
    public File getTestedApk(int density, @NonNull List<String> abis) {
        TestedVariantData testedVariantData = testVariantData.getTestedVariantData();
        BaseVariantData<?> testedVariantData2 = (BaseVariantData) testedVariantData;

        SplitOutput output = SplitOutputMatcher.computeBestOutput(
                testedVariantData2.getOutputs(), density, abis);
        if (output != null) {
            return output.getOutputFile();
        }

        return null;
    }

    @Nullable
    @Override
    public File[] getSplitApks() {
        TestedVariantData testedVariantData = testVariantData.getTestedVariantData();
        BaseVariantData<?> testedVariantData2 = (BaseVariantData) testedVariantData;

        ArrayList<File> splits = new ArrayList<File>();
        for (ApkOutput apkOutput : testedVariantData2.getOutputs().get(0).getOutputFiles()) {
            if (apkOutput.getType() == ApkOutput.OutputType.SPLIT) {
                splits.add(apkOutput.getOutputFile());
            }
        }
        return splits.isEmpty() ? null : splits.toArray(new File[splits.size()]);
    }
}
