/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.builder.model.VectorDrawablesOptions;

import java.util.Set;

/**
 * Adaptor from managed.VectorDrawablesOptions to model.VectorDrawablesOptions.
 */
public class VectorDrawablesOptionsAdaptor implements VectorDrawablesOptions {

    @NonNull
    private com.android.build.gradle.managed.VectorDrawablesOptions managedOptions;

    public VectorDrawablesOptionsAdaptor(
            @NonNull com.android.build.gradle.managed.VectorDrawablesOptions managedOptions) {
        this.managedOptions = managedOptions;
    }

    @Nullable
    @Override
    public Set<String> getGeneratedDensities() {
        return managedOptions.getGeneratedDensities();
    }

    @Nullable
    @Override
    public Boolean getUseSupportLibrary() {
        return managedOptions.getUseSupportLibrary();
    }
}
