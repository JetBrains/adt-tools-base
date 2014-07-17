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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;

import org.gradle.api.Task;

import java.io.File;

/**
 * Implementation of the base variant output. This is the base class for items common to apps,
 * test apps, and libraries
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class BaseVariantOutputImpl implements BaseVariantOutput {

    @NonNull
    protected abstract BaseVariantOutputData getVariantOutputData();

    @Override
    public void setOutputFile(@NonNull File file) {
        getVariantOutputData().setOutputFile(file);
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return getVariantOutputData().getOutputFile();
    }

    @NonNull
    @Override
    public ProcessAndroidResources getProcessResources() {
        return getVariantOutputData().processResourcesTask;
    }

    @NonNull
    @Override
    public ManifestProcessorTask getProcessManifest() {
        return getVariantOutputData().manifestProcessorTask;
    }

    @Nullable
    @Override
    public Task getAssemble() {
        return getVariantOutputData().assembleTask;
    }
}
