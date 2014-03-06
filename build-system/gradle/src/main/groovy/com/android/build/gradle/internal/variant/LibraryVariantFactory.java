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
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.api.LibraryVariantImpl;
import com.android.builder.VariantConfiguration;

import org.gradle.api.Task;

/**
 */
public class LibraryVariantFactory implements VariantFactory {

    @NonNull
    private final BasePlugin basePlugin;

    public LibraryVariantFactory(@NonNull BasePlugin basePlugin) {
        this.basePlugin = basePlugin;
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(@NonNull VariantConfiguration variantConfiguration) {
        return new LibraryVariantData(variantConfiguration);
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(@NonNull BaseVariantData variantData) {
        return basePlugin.getInstantiator().newInstance(LibraryVariantImpl.class, variantData);
    }

    @Override
    public void createTasks(@NonNull BaseVariantData variantData, @Nullable Task assembleTask) {

    }
}
