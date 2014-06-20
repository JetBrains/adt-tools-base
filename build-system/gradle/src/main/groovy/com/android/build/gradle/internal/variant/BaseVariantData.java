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
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.StringHelper;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.Lists;

import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import groovy.lang.Closure;

/**
 * Base data about a variant.
 */
public abstract class BaseVariantData {

    private final VariantConfiguration variantConfiguration;
    private VariantDependencies variantDependency;

    public Task preBuildTask;
    public PrepareDependenciesTask prepareDependenciesTask;
    public Task sourceGenTask;
    public Task resourceGenTask;
    public Task assetGenTask;
    public CheckManifest checkManifestTask;

    public ManifestProcessorTask manifestProcessorTask;
    public RenderscriptCompile renderscriptCompileTask;
    public AidlCompile aidlCompileTask;
    public MergeResources mergeResourcesTask;
    public MergeAssets mergeAssetsTask;
    public ProcessAndroidResources processResourcesTask;
    public GenerateBuildConfig generateBuildConfigTask;
    public GenerateResValues generateResValuesTask;
    public Copy copyApkTask;
    public GenerateApkDataTask generateApkDataTask;

    public JavaCompile javaCompileTask;
    public Task obfuscationTask;
    public Copy processJavaResourcesTask;
    public NdkCompile ndkCompileTask;

    private Object outputFile;
    private Object[] javaSources;

    public Task assembleTask;

    private List<File> extraGeneratedSourceFolders;

    public BaseVariantData(@NonNull VariantConfiguration variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
        variantConfiguration.checkSourceProviders();
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

    @NonNull
    public String getPackageName() {
        return variantConfiguration.getApplicationId();
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
        sourceGenTask.dependsOn(task);

        for (File f : generatedSourceFolders) {
            javaCompileTask.source(f);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        sourceGenTask.dependsOn(task);

        for (File f : generatedSourceFolders) {
            javaCompileTask.source(f);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    /**
     * Computes the Java sources to use for compilation. This Object[] contains
     * {@link org.gradle.api.file.FileCollection} and {@link File} instances
     */
    @NonNull
    public Object[] getJavaSources() {
        if (javaSources == null) {
            // Build the list of source folders.
            List<Object> sourceList = Lists.newArrayList();

            // First the actual source folders.
            List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
            for (SourceProvider provider : providers) {
                sourceList.add(((AndroidSourceSet) provider).getJava().getSourceFiles());
            }

            // then all the generated src folders.
            sourceList.add(processResourcesTask.getSourceOutputDir());
            sourceList.add(generateBuildConfigTask.getSourceOutputDir());
            sourceList.add(aidlCompileTask.getSourceOutputDir());
            if (!variantConfiguration.getMergedFlavor().getRenderscriptNdkMode()) {
                sourceList.add(renderscriptCompileTask.getSourceOutputDir());
            }

            javaSources = sourceList.toArray();
        }

        return javaSources;
    }

    /**
     * Returns the Java folders needed for code coverage report.
     *
     * This includes all the source folders except for the ones containing R and buildConfig.
     */
    @NonNull
    public List<File> getJavaSourceFoldersForCoverage() {
        // Build the list of source folders.
        List<File> sourceFolders = Lists.newArrayList();

        // First the actual source folders.
        List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
        for (SourceProvider provider : providers) {
            for (File sourceFolder : provider.getJavaDirectories()) {
                if (sourceFolder.isDirectory()) {
                    sourceFolders.add(sourceFolder);
                }
            }
        }

        File sourceFolder;
        // then all the generated src folders, except the ones for the R/Manifest and
        // BuildConfig classes.
        sourceFolder = aidlCompileTask.getSourceOutputDir();
        if (sourceFolder.isDirectory()) {
            sourceFolders.add(sourceFolder);
        }

        if (!variantConfiguration.getMergedFlavor().getRenderscriptNdkMode()) {
            sourceFolder = renderscriptCompileTask.getSourceOutputDir();
            if (sourceFolder.isDirectory()) {
                sourceFolders.add(sourceFolder);
            }
        }

        return sourceFolders;
    }

}
