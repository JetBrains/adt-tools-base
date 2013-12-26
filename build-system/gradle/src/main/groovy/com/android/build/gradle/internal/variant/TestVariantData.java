/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.builder.VariantConfiguration;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Data about a variant that produce a test APK
 */
public class TestVariantData extends ApkVariantData {

    public DeviceProviderInstrumentTestTask connectedTestTask;
    public final List<DeviceProviderInstrumentTestTask> providerTestTaskList = Lists.newArrayList();
    @NonNull
    private final TestedVariantData testedVariantData;

    public TestVariantData(@NonNull VariantConfiguration config,
                           @NonNull TestedVariantData testedVariantData) {
        super(config);
        this.testedVariantData = testedVariantData;
    }

    @NonNull
    public TestedVariantData getTestedVariantData() {
        return testedVariantData;
    }

    @Override
    @NonNull
    public String getDescription() {
        if (getVariantConfiguration().hasFlavors()) {
            return String.format("Test build for the %s%s build",
                    getCapitalizedFlavorName(), getCapitalizedBuildTypeName());
        } else {
            return String.format("Test build for the %s build",
                    getCapitalizedBuildTypeName());
        }
    }

    @Override
    public boolean getZipAlign() {
        return false;
    }
}
