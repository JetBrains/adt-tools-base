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
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.ApiVersion;
import com.android.builder.testing.TestData;

import java.util.Locale;

/**
 * Common implementation of {@link TestData} for embedded test projects (in androidTest folder)
 * and separate module test projects.
 */
public abstract class AbstractTestDataImpl implements TestData {

    @NonNull
    private final VariantConfiguration testVariantConfig;

    public AbstractTestDataImpl(@NonNull VariantConfiguration testVariantConfig) {
        this.testVariantConfig = testVariantConfig;
    }

    @NonNull
    @Override
    public String getInstrumentationRunner() {
        return testVariantConfig.getInstrumentationRunner();
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

    @NonNull
    @Override
    public String getFlavorName() {
        return testVariantConfig.getFlavorName().toUpperCase(Locale.getDefault());
    }
}
