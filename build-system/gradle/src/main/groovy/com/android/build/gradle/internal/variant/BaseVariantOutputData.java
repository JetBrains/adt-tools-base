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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;

import org.gradle.api.Task;

import java.io.File;

/**
 * Base output data about a variant.
 */
public abstract class BaseVariantOutputData {

    private static final String UNIVERSAL = "universal";

    @NonNull
    protected final BaseVariantData<?> variantData;

    @Nullable
    private final String densityFilter;
    @Nullable
    private final String abiFilter;

    public ManifestProcessorTask manifestProcessorTask;
    public ProcessAndroidResources processResourcesTask;
    public Task assembleTask;

    public BaseVariantOutputData(
            @Nullable String densityFilter,
            @Nullable String abiFilter,
            @NonNull BaseVariantData<?> variantData) {
        this.densityFilter = densityFilter;
        this.abiFilter = abiFilter;
        this.variantData = variantData;
    }

    @Nullable
    public String getDensityFilter() {
        return densityFilter;
    }

    @Nullable
    public String getAbiFilter() {
        return abiFilter;
    }

    public abstract void setOutputFile(@NonNull File file);
    @NonNull
    public abstract File getOutputFile();

    @NonNull
    public String getFullName() {
        if (densityFilter == null) {
            return variantData.getVariantConfiguration().computeFullNameWithSplits(UNIVERSAL);
        }

        return variantData.getVariantConfiguration().computeFullNameWithSplits(densityFilter);
    }

    @NonNull
    public String getBaseName() {
        if (densityFilter == null) {
            return variantData.getVariantConfiguration().computeBaseNameWithSplits(UNIVERSAL);
        }

        return variantData.getVariantConfiguration().computeBaseNameWithSplits(densityFilter);
    }

    @NonNull
    public String getDirName() {
        if (densityFilter == null) {
            return variantData.getVariantConfiguration().computeDirNameWithSplits(UNIVERSAL);
        }

        return variantData.getVariantConfiguration().computeDirNameWithSplits(densityFilter);
    }
}
