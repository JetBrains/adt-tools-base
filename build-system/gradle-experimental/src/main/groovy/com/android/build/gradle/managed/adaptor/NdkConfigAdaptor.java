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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.managed.NdkConfig;
import com.google.common.base.Joiner;

import java.util.List;
import java.util.Set;

/**
 * An adaptor to convert a NdkConfig to NdkConfig.
 */
public class NdkConfigAdaptor implements com.android.build.gradle.internal.core.NdkConfig {

    NdkConfig ndkConfig;

    public NdkConfigAdaptor(@NonNull NdkConfig ndkConfig) {
        this.ndkConfig = ndkConfig;
    }

    @Nullable
    @Override
    public String getModuleName() {
        return ndkConfig.getModuleName();
    }

    @Nullable
    @Override
    public String getcFlags() {
        return Joiner.on(' ').join(ndkConfig.getCFlags());
    }

    @Nullable
    @Override
    public List<String> getLdLibs() {
        return ndkConfig.getLdLibs();
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        // null is considered to be no filter, which is different from an empty filter list.
        return (ndkConfig.getAbiFilters().isEmpty() ? null : ndkConfig.getAbiFilters());
    }

    @Nullable
    @Override
    public String getStl() {
        return ndkConfig.getStl();
    }

    @Nullable
    @Override
    public Integer getJobs() {
        return null;
    }
}
