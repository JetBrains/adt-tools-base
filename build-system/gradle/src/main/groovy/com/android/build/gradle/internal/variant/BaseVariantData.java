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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.StringHelper;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.VariantConfiguration;
import com.google.common.collect.Lists;

import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import groovy.lang.Closure;
import proguard.gradle.ProGuardTask;

/**
 * Base data about a variant.
 */
public abstract class BaseVariantData {

    private final VariantConfiguration variantConfiguration;
    private VariantDependencies variantDependency;

    public Task preBuildTask;
    public PrepareDependenciesTask prepareDependenciesTask;
    public Task sourceGenTask;
    public CheckManifest checkManifestTask;

    public ProcessManifest processManifestTask;
    public RenderscriptCompile renderscriptCompileTask;
    public AidlCompile aidlCompileTask;
    public MergeResources mergeResourcesTask;
    public MergeAssets mergeAssetsTask;
    public ProcessAndroidResources processResourcesTask;
    public GenerateBuildConfig generateBuildConfigTask;

    public JavaCompile javaCompileTask;
    public ProGuardTask proguardTask;
    public Copy processJavaResourcesTask;
    public NdkCompile ndkCompileTask;

    private Object outputFile;

    public Task assembleTask;

    private List<File> extraGeneratedSourceFolders;

    public BaseVariantData(@NonNull VariantConfiguration variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @NonNull
    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantDependency(@NonNull VariantDependencies variantDependency) {
        this.variantDependency = variantDependency;
    }

    @NonNull
    public VariantDependencies getVariantDependency() {
        return variantDependency;
    }

    @NonNull
    public abstract String getDescription();

    @Nullable
    public String getPackageName() {
        return variantConfiguration.getPackageName();
    }

    @NonNull
    protected String getCapitalizedBuildTypeName() {
        return StringHelper.capitalize(variantConfiguration.getBuildType().getName());
    }

    @NonNull
    protected String getCapitalizedFlavorName() {
        return StringHelper.capitalize(variantConfiguration.getFlavorName());
    }

    public void setOutputFile(Object file) {
        outputFile = file;
    }

    public File getOutputFile() {
        if (outputFile instanceof File) {
            return (File) outputFile;
        } else if (outputFile instanceof Closure) {
            Closure c = (Closure) outputFile;
            return (File) c.call();
        }

        assert false;
        return null;
    }

    @VisibleForTesting
    @NonNull
    String getName() {
        return variantConfiguration.getFullName();
    }

    @Nullable
    public List<File> getExtraGeneratedSourceFolders() {
        return extraGeneratedSourceFolders;
    }

    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        if (extraGeneratedSourceFolders == null) {
            extraGeneratedSourceFolders = Lists.newArrayList();
        }

        Collections.addAll(extraGeneratedSourceFolders, generatedSourceFolders);
    }

    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        if (extraGeneratedSourceFolders == null) {
            extraGeneratedSourceFolders = Lists.newArrayList();
        }

        extraGeneratedSourceFolders.addAll(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... generatedSourceFolders) {
        if (extraGeneratedSourceFolders == null) {
            extraGeneratedSourceFolders = Lists.newArrayList();
        }

        javaCompileTask.dependsOn(task);

        for (File f : generatedSourceFolders) {
            javaCompileTask.source(f);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        if (extraGeneratedSourceFolders == null) {
            extraGeneratedSourceFolders = Lists.newArrayList();
        }

        javaCompileTask.dependsOn(task);

        for (File f : generatedSourceFolders) {
            javaCompileTask.source(f);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }
}
