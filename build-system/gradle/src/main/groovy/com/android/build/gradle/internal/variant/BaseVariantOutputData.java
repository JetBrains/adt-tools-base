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
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;

import org.gradle.api.Task;

import java.io.File;

import groovy.lang.Closure;

/**
 * Base output data about a variant.
 */
public abstract class BaseVariantOutputData {

    public ManifestProcessorTask manifestProcessorTask;
    public ProcessAndroidResources processResourcesTask;
    public Task assembleTask;

    public abstract void setOutputFile(@NonNull File file);
    @NonNull
    public abstract File getOutputFile();
}
