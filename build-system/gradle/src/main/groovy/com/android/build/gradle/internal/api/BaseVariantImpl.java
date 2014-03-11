/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.DefaultBuildType;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.model.SourceProvider;

import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.List;

abstract class BaseVariantImpl implements BaseVariant {

    @NonNull
    protected abstract BaseVariantData getVariantData();

    @Override
    @NonNull
    public String getName() {
        return getVariantData().getVariantConfiguration().getFullName();
    }

    @Override
    @NonNull
    public String getDescription() {
        return getVariantData().getDescription();
    }

    @Override
    @NonNull
    public String getDirName() {
        return getVariantData().getVariantConfiguration().getDirName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return getVariantData().getVariantConfiguration().getBaseName();
    }

    @NonNull
    @Override
    public String getFlavorName() {
        return getVariantData().getVariantConfiguration().getFlavorName();
    }

    @Override
    @NonNull
    public DefaultBuildType getBuildType() {
        return getVariantData().getVariantConfiguration().getBuildType();
    }

    @Override
    @NonNull
    public DefaultProductFlavor getMergedFlavor() {
        return getVariantData().getVariantConfiguration().getMergedFlavor();
    }

    @NonNull
    @Override
    public List<SourceProvider> getSourceSets() {
        return getVariantData().getVariantConfiguration().getSortedSourceProviders();
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return getVariantData().getOutputFile();
    }

    @Override
    @NonNull
    public Task getPreBuild() {
        return getVariantData().preBuildTask;
    }

    @Override
    @NonNull
    public Task getCheckManifest() {
        return getVariantData().checkManifestTask;
    }

    @Override
    @NonNull
    public ProcessManifest getProcessManifest() {
        return getVariantData().processManifestTask;
    }

    @Override
    @NonNull
    public AidlCompile getAidlCompile() {
        return getVariantData().aidlCompileTask;
    }

    @Override
    @NonNull
    public RenderscriptCompile getRenderscriptCompile() {
        return getVariantData().renderscriptCompileTask;
    }

    @Override
    public MergeResources getMergeResources() {
        return getVariantData().mergeResourcesTask;
    }

    @Override
    public MergeAssets getMergeAssets() {
        return getVariantData().mergeAssetsTask;
    }

    @Override
    @NonNull
    public ProcessAndroidResources getProcessResources() {
        return getVariantData().processResourcesTask;
    }

    @Override
    public GenerateBuildConfig getGenerateBuildConfig() {
        return getVariantData().generateBuildConfigTask;
    }

    @Override
    @NonNull
    public JavaCompile getJavaCompile() {
        return getVariantData().javaCompileTask;
    }

    @NonNull
    @Override
    public NdkCompile getNdkCompile() {
        return getVariantData().ndkCompileTask;
    }

    @Override
    @NonNull
    public Copy getProcessJavaResources() {
        return getVariantData().processJavaResourcesTask;
    }

    @Override
    @Nullable
    public Task getAssemble() {
        return getVariantData().assembleTask;
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        getVariantData().addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }

    @Override
    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> sourceFolders) {
        getVariantData().registerJavaGeneratingTask(task, sourceFolders);
    }
}
