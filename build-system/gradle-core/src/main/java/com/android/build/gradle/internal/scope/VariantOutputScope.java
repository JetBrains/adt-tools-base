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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.SplitZipAlign;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.packaging.NativeLibrariesPackagingMode;
import com.android.utils.StringHelper;

import org.gradle.api.DefaultTask;

import java.io.File;
import java.util.Collection;

/**
 * A scope containing data for a specific variant.
 */
public class VariantOutputScope implements TransformVariantScope {

    @NonNull
    private VariantScope variantScope;
    @NonNull
    private BaseVariantOutputData variantOutputData;

    // Tasks
    private AndroidTask<DefaultTask> assembleTask;

    private AndroidTask<CompatibleScreensManifest> compatibleScreensManifestTask;

    private AndroidTask<? extends ManifestProcessorTask> manifestProcessorTask;

    private AndroidTask<ProcessAndroidResources> processResourcesTask;

    private AndroidTask<?> shrinkResourcesTask;

    private AndroidTask<SplitZipAlign> splitZipAlignTask;

    public VariantOutputScope(
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantOutputData variantOutputData) {
        this.variantScope = variantScope;
        this.variantOutputData = variantOutputData;
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return variantScope.getGlobalScope();
    }

    @NonNull
    public VariantScope getVariantScope() {
        return variantScope;
    }

    @NonNull
    public BaseVariantOutputData getVariantOutputData() {
        return variantOutputData;
    }

    @NonNull
    @Override
    public String getDirName() {
        // this is here as a safety net in the Transform manager which handles either VariantScope
        // or VariantOutputScope. Should this ever be called we'll need to compute this properly.
        throw new UnsupportedOperationException("dir name per output scope not yet supported");
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        // this is here as a safety net in the Transform manager which handles either VariantScope
        // or VariantOutputScope. Should this ever be called we'll need to compute this properly.
        throw new UnsupportedOperationException("dir name per output scope not yet supported");
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return variantScope.getFullVariantName();
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + StringHelper.capitalize(getVariantOutputData().getFullName()) + suffix;
    }

    @NonNull
    public File getPackageApk() {
        ApkVariantData apkVariantData = (ApkVariantData) variantScope.getVariantData();

        boolean useOldPackaging = AndroidGradleOptions.useOldPackaging(
                getGlobalScope().getProject());

        // New packaging also signs so eventually I will fix this :)
        boolean signedApk = apkVariantData.isSigned();
        String apkName = signedApk ?
                getGlobalScope().getProjectBaseName() + "-" + variantOutputData.getBaseName()
                        + (useOldPackaging ? "-unaligned.apk" : ".apk"):
                getGlobalScope().getProjectBaseName() + "-" + variantOutputData.getBaseName()
                        + "-unsigned.apk";

        return new File(getGlobalScope().getApkLocation(), apkName);
    }

    @NonNull
    public File getManifestOutputFile() {
        switch(variantScope.getVariantConfiguration().getType()) {
            case DEFAULT:
                return new File(getGlobalScope().getIntermediatesDir(),
                        "/manifests/full/"  + variantOutputData.getDirName()
                                + "/AndroidManifest.xml");
            case LIBRARY:
                return new File(variantScope.getBaseBundleDir(), "AndroidManifest.xml");
            case ANDROID_TEST:
                return new File(getGlobalScope().getIntermediatesDir(),
                        "manifest/" + variantScope.getVariantConfiguration().getDirName()
                                + "/AndroidManifest.xml");
            default:
                throw new RuntimeException(
                        "getManifestOutputFile called for an unexpected variant.");
        }
    }

    @NonNull
    public File getCompatibleScreensManifestFile() {
        return new File(getGlobalScope().getIntermediatesDir(),
                "/manifests/density/" + variantOutputData.getDirName() + "/AndroidManifest.xml");

    }

    @NonNull
    public File getProcessResourcePackageOutputFile() {
        return new File(getGlobalScope().getIntermediatesDir(),
                "res/resources-" + variantOutputData.getBaseName() + ".ap_");
    }

    @NonNull
    public File getShrinkedResourcesFile() {
        return new File(getGlobalScope().getIntermediatesDir(), "/res/" +
                "resources-" + variantOutputData.getBaseName() + "-stripped.ap_");
    }

    @NonNull
    public File getFinalResourcesFile() {
        return variantScope.useResourceShrinker()
                ? getShrinkedResourcesFile()
                : getProcessResourcePackageOutputFile();
    }

    // Tasks
    public AndroidTask<DefaultTask> getAssembleTask() {
        return assembleTask;
    }

    public void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask) {
        this.assembleTask = assembleTask;
    }

    @Nullable
    public AndroidTask<CompatibleScreensManifest> getCompatibleScreensManifestTask() {
        return compatibleScreensManifestTask;
    }

    public void setCompatibleScreensManifestTask(
            @Nullable AndroidTask<CompatibleScreensManifest> compatibleScreensManifestTask) {
        this.compatibleScreensManifestTask = compatibleScreensManifestTask;
    }

    public AndroidTask<? extends ManifestProcessorTask> getManifestProcessorTask() {
        return manifestProcessorTask;
    }

    public void setManifestProcessorTask(
            AndroidTask<? extends ManifestProcessorTask> manifestProcessorTask) {
        this.manifestProcessorTask = manifestProcessorTask;
    }

    public AndroidTask<ProcessAndroidResources> getProcessResourcesTask() {
        return processResourcesTask;
    }

    public void setProcessResourcesTask(
            AndroidTask<ProcessAndroidResources> processResourcesTask) {
        this.processResourcesTask = processResourcesTask;
    }

    public AndroidTask<?> getShrinkResourcesTask() {
        return shrinkResourcesTask;
    }

    public void setShrinkResourcesTask(
            AndroidTask<?> shrinkResourcesTask) {
        this.shrinkResourcesTask = shrinkResourcesTask;
    }

    public AndroidTask<SplitZipAlign> getSplitZipAlignTask() {
        return splitZipAlignTask;
    }

    public void setSplitZipAlignTask(
            AndroidTask<SplitZipAlign> splitZipAlignTask) {
        this.splitZipAlignTask = splitZipAlignTask;
    }

    @NonNull
    public ApkOutputFile getMainOutputFile() {
        return getVariantOutputData().getMainOutputFile();
    }

    public NativeLibrariesPackagingMode getNativeLibrariesPackagingMode() {
        DefaultManifestParser parser = new DefaultManifestParser(getManifestOutputFile());
        Boolean extractNativeLibs = parser.getExtractNativeLibs();

        // The default is "true", so we only package *.so files differently if the user explicitly
        // set this to "false".
        if (Boolean.FALSE.equals(extractNativeLibs)) {
            return NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED;
        } else {
            return NativeLibrariesPackagingMode.COMPRESSED;
        }
    }
}
