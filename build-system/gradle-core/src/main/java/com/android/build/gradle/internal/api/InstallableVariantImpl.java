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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.InstallableVariant;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.build.gradle.internal.variant.InstallableVariantData;
import com.android.builder.core.AndroidBuilder;

import org.gradle.api.DefaultTask;

/**
 * Implementation of an installable variant.
 */
public abstract class InstallableVariantImpl extends AndroidArtifactVariantImpl implements InstallableVariant {

    protected InstallableVariantImpl(@NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider) {
        super(androidBuilder, immutableObjectProvider);
    }

    @NonNull
    protected abstract InstallableVariantData<?> getInstallableVariantData();

    @NonNull
    @Override
    protected AndroidArtifactVariantData<?> getAndroidArtifactVariantData() {
        return getInstallableVariantData();
    }

    @Override
    public DefaultTask getInstall() {
        return getInstallableVariantData().installTask;
    }

    @Override
    public DefaultTask getUninstall() {
        return getInstallableVariantData().uninstallTask;
    }
}
