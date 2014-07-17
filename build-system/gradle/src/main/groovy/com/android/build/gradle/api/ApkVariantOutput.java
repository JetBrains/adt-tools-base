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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ZipAlign;

import org.gradle.api.DefaultTask;

import java.io.File;

/**
 * A variant output for apk-generating variants.
 */
public interface ApkVariantOutput extends BaseVariantOutput {

    /**
     * Returns the packaging task
     */
    @Nullable
    PackageApplication getPackageApplication();

    /**
     * Returns the Zip align task.
     */
    @Nullable
    ZipAlign getZipAlign();

    @NonNull
    ZipAlign createZipAlignTask(@NonNull String taskName, @NonNull File inputFile, @NonNull File outputFile);

    /**
     * Returns the installation task.
     *
     * Even for variant for regular project, this can be null if the app cannot be signed.
     */
    @Nullable
    DefaultTask getInstall();
}
