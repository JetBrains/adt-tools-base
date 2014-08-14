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
import com.android.build.SplitOutput;
import com.android.build.gradle.api.ApkOutput;
import com.android.build.gradle.internal.StringHelper;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.google.common.collect.ImmutableList;

import org.gradle.api.Task;

import java.io.File;

/**
 * Base output data about a variant.
 */
public abstract class BaseVariantOutputData implements SplitOutput {

    private static final String UNIVERSAL = "universal";

    @NonNull
    protected final BaseVariantData<?> variantData;

    @Nullable
    private final String densityFilter;
    @Nullable
    private final String abiFilter;

    private boolean multiOutput = false;

    public ManifestProcessorTask manifestProcessorTask;
    public ProcessAndroidResources processResourcesTask;
    public PackageSplitRes packageSplitResourcesTask;
    public Task assembleTask;

    public BaseVariantOutputData(
            @Nullable String densityFilter,
            @Nullable String abiFilter,
            @NonNull BaseVariantData<?> variantData) {
        this.densityFilter = densityFilter;
        this.abiFilter = abiFilter;
        this.variantData = variantData;
    }

    @Override
    @Nullable
    public String getDensityFilter() {
        return densityFilter;
    }

    @Override
    @Nullable
    public String getAbiFilter() {
        return abiFilter;
    }

    public abstract void setOutputFile(@NonNull File file);

    @NonNull
    public abstract File getOutputFile();

    public abstract ImmutableList<ApkOutput> getOutputFiles();

    @NonNull
    public String getFullName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getFullName();
        }
        return variantData.getVariantConfiguration().computeFullNameWithSplits(getFilterName());
    }

    @NonNull
    public String getBaseName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getBaseName();
        }
        return variantData.getVariantConfiguration().computeBaseNameWithSplits(getFilterName());
    }

    @NonNull
    public String getDirName() {
        if (!multiOutput) {
            return variantData.getVariantConfiguration().getDirName();
        }
        return variantData.getVariantConfiguration().computeDirNameWithSplits(densityFilter,
                abiFilter);
    }

    @NonNull
    private String getFilterName() {
        if (densityFilter == null && abiFilter == null) {
            return UNIVERSAL;
        }

        StringBuilder sb = new StringBuilder();
        if (densityFilter != null) {
            sb.append(densityFilter);
        }
        if (abiFilter != null) {
            if (sb.length() > 0) {
                sb.append(StringHelper.capitalize(abiFilter));
            } else {
                sb.append(abiFilter);
            }
        }

        return sb.toString();
    }

    void setMultiOutput(boolean multiOutput) {
        this.multiOutput = multiOutput;
    }
}
