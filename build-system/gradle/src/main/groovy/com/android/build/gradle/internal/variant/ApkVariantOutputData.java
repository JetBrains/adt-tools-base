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
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ZipAlign;

import org.gradle.api.DefaultTask;

import java.io.File;

/**
 * Base output data for a variant that generates an APK file.
 */
public class ApkVariantOutputData extends BaseVariantOutputData {

    public PackageApplication packageApplicationTask;
    public ZipAlign zipAlignTask;

    public DefaultTask installTask;

    private int versionCodeOverride = -1;

    public ApkVariantOutputData(
            @Nullable String densityFilter,
            @Nullable String abiFilter,
            @NonNull BaseVariantData variantData) {
        super(densityFilter, abiFilter, variantData);
    }

    @Override
    public void setOutputFile(@NonNull File file) {
        if (zipAlignTask != null) {
            zipAlignTask.setOutputFile(file);
        } else {
            packageApplicationTask.setOutputFile(file);
        }
    }

    @NonNull
    @Override
    public File getOutputFile() {
        if (zipAlignTask != null) {
            return zipAlignTask.getOutputFile();
        }

        return packageApplicationTask.getOutputFile();
    }

    @NonNull
    public ZipAlign createZipAlignTask(@NonNull String taskName, @NonNull File inputFile,
            @NonNull File outputFile) {
        //noinspection VariableNotUsedInsideIf
        if (zipAlignTask != null) {
            throw new RuntimeException(String.format(
                    "ZipAlign task for variant '%s' already exists.", variantData.getName()));
        }

        zipAlignTask = variantData.basePlugin.createZipAlignTask(taskName, inputFile, outputFile);

        // setup dependencies
        assembleTask.dependsOn(zipAlignTask);

        return zipAlignTask;
    }

    public int getVersionCode() {
        if (versionCodeOverride > 0) {
            return versionCodeOverride;
        }

        return variantData.getVariantConfiguration().getMergedFlavor().getVersionCode();
    }

    public void setVersionCodeOverride(int versionCodeOverride) {
        this.versionCodeOverride = versionCodeOverride;
    }

    public int getVersionCodeOverride() {
        return versionCodeOverride;
    }
}
