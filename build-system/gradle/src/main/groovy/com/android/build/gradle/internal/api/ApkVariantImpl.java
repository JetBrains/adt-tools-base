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
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.tasks.Dex;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ZipAlign;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.model.SigningConfig;

import org.gradle.api.DefaultTask;

import java.io.File;
import java.util.List;

public abstract class ApkVariantImpl extends BaseVariantImpl implements ApkVariant {

    @NonNull
    private BasePlugin plugin;

    protected ApkVariantImpl(@NonNull BasePlugin plugin) {
        this.plugin = plugin;
    }

    @NonNull
    protected abstract ApkVariantData getApkVariantData();

    @Override
    @NonNull
    public List<DefaultProductFlavor> getProductFlavors() {
        return getVariantData().getVariantConfiguration().getFlavorConfigs();
    }

    @Override
    public void setOutputFile(@NonNull File outputFile) {
        ApkVariantData variantData = getApkVariantData();
        if (variantData.zipAlignTask != null) {
            variantData.zipAlignTask.setOutputFile(outputFile);
        } else {
            variantData.packageApplicationTask.setOutputFile(outputFile);
        }

        // also set it on the variant Data so that the values are in sync
        variantData.setOutputFile(outputFile);
    }

    @Override
    public Dex getDex() {
        return getApkVariantData().dexTask;
    }

    @Override
    public PackageApplication getPackageApplication() {
        return getApkVariantData().packageApplicationTask;
    }

    @Override
    public ZipAlign getZipAlign() {
        return getApkVariantData().zipAlignTask;
    }

    @Override
    public DefaultTask getInstall() {
        return getApkVariantData().installTask;
    }

    @Override
    public DefaultTask getUninstall() {
        return getApkVariantData().uninstallTask;
    }

    @Override
    public SigningConfig getSigningConfig() {
        return getApkVariantData().getVariantConfiguration().getSigningConfig();
    }

    @Override
    public boolean isSigningReady() {
        return getApkVariantData().isSigned();
    }

    @Override
    @NonNull
    public ZipAlign createZipAlignTask(
            @NonNull String taskName,
            @NonNull File inputFile,
            @NonNull File outputFile) {
        ApkVariantData variantData = getApkVariantData();

        //noinspection VariableNotUsedInsideIf
        if (variantData.zipAlignTask != null) {
            throw new RuntimeException(String.format(
                    "ZipAlign task for variant '%s' already exists.", getName()));
        }

        ZipAlign task = plugin.createZipAlignTask(taskName, inputFile, outputFile);

        // update variant data
        variantData.setOutputFile(outputFile);
        variantData.zipAlignTask = task;

        // setup dependencies
        variantData.assembleTask.dependsOn(task);

        return task;
    }
}
