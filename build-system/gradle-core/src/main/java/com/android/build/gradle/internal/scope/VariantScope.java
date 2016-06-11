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
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.builder.model.ApiVersion;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A scope containing data for a specific variant.
 */
public interface VariantScope extends TransformVariantScope, InstantRunVariantScope {

    @Override
    @NonNull
    GlobalScope getGlobalScope();

    @NonNull
    GradleVariantConfiguration getVariantConfiguration();

    @NonNull
    BaseVariantData<? extends BaseVariantOutputData> getVariantData();

    boolean isMinifyEnabled();

    boolean useResourceShrinker();

    boolean isJackEnabled();

    @NonNull
    ApiVersion getMinSdkVersion();

    @NonNull
    TransformManager getTransformManager();

    @Nullable
    Collection<Object> getNdkBuildable();

    void setNdkBuildable(@NonNull Collection<Object> ndkBuildable);

    @Nullable
    Collection<File> getNdkSoFolder();

    void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder);

    @Nullable
    File getNdkObjFolder();

    void setNdkObjFolder(@NonNull File ndkObjFolder);

    @Nullable
    File getNdkDebuggableLibraryFolders(@NonNull Abi abi);

    void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath);

    @NonNull
    File getDexOutputFolder();

    @Nullable
    BaseVariantData getTestedVariantData();


    @NonNull
    File getInstantRunSplitApkOutputFolder();

    @NonNull
    FileCollection getJavaClasspath();

    @NonNull
    File getJavaOutputDir();

    @NonNull
    Iterable<File> getJavaOutputs();

    @NonNull
    File getJavaDependencyCache();

    @NonNull
    File getPreDexOutputDir();

    @NonNull
    File getProguardOutputFile();

    @NonNull
    File getProguardComponentsJarFile();

    @NonNull
    File getJarMergingOutputFile();

    @NonNull
    File getManifestKeepListProguardFile();

    @NonNull
    File getMainDexListFile();

    @NonNull
    File getRenderscriptSourceOutputDir();

    @NonNull
    File getRenderscriptLibOutputDir();

    @NonNull
    File getSymbolLocation();

    @NonNull
    File getFinalResourcesDir();

    void setResourceOutputDir(@NonNull File resourceOutputDir);

    @NonNull
    File getDefaultMergeResourcesOutputDir();

    @NonNull
    File getMergeResourcesOutputDir();

    void setMergeResourceOutputDir(@Nullable File mergeResourceOutputDir);

    @NonNull
    File getResourceBlameLogDir();

    @NonNull
    File getMergeAssetsOutputDir();

    @NonNull
    File getMergeNativeLibsOutputDir();

    @NonNull
    File getMergeShadersOutputDir();

    @NonNull
    File getBuildConfigSourceOutputDir();

    @NonNull
    File getGeneratedResOutputDir();

    @NonNull
    File getGeneratedPngsOutputDir();

    @NonNull
    File getRenderscriptResOutputDir();

    @NonNull
    File getRenderscriptObjOutputDir();

    @NonNull
    File getPackagedJarsJavaResDestinationDir();

    @NonNull
    File getSourceFoldersJavaResDestinationDir();

    @NonNull
    File getJavaResourcesDestinationDir();

    @NonNull
    File getRClassSourceOutputDir();

    @NonNull
    File getAidlSourceOutputDir();

    @NonNull
    File getShadersOutputDir();

    @NonNull
    File getPackagedAidlDir();

    /**
     * Returns a place to store incremental build data. The {@code name} argument has to be unique
     * per task, ideally generated with {@link TaskConfigAction#getName()}.
     */
    @NonNull
    File getIncrementalDir(String name);

    @NonNull
    File getJackEcjOptionsFile();

    @NonNull
    File getJackClassesZip();

    @NonNull
    File getJackCoverageMetadataFile();

    @NonNull
    File getCoverageReportDir();

    @NonNull
    File getClassOutputForDataBinding();

    @NonNull
    File getLayoutInfoOutputForDataBinding();

    @NonNull
    File getLayoutFolderOutputForDataBinding();

    @NonNull
    File getGeneratedClassListOutputFileForDataBinding();

    @NonNull
    File getProguardOutputFolder();

    @NonNull
    File getProcessAndroidResourcesProguardOutputFile();

    File getMappingFile();

    @NonNull
    File getGenerateSplitAbiResOutputDirectory();

    @NonNull
    File getSplitOutputDirectory();

    @NonNull
    List<File> getSplitAbiResOutputFiles();

    @NonNull
    List<File> getPackageSplitAbiOutputFiles();

    @NonNull
    File getAaptFriendlyManifestOutputFile();

    @NonNull
    File getInstantRunManifestOutputFile();

    @NonNull
    File  getManifestReportFile();

    @NonNull
    File getMicroApkManifestFile();

    @NonNull
    File getMicroApkResDirectory();

    @NonNull
    File getBaseBundleDir();

    @NonNull
    File getAnnotationProcessorOutputDir();

    AndroidTask<DefaultTask> getAssembleTask();

    void setAssembleTask(@NonNull AndroidTask<DefaultTask> assembleTask);

    AndroidTask<DefaultTask> getPreBuildTask();

    void setPreBuildTask(AndroidTask<DefaultTask> preBuildTask);

    AndroidTask<PrepareDependenciesTask> getPrepareDependenciesTask();

    void setPrepareDependenciesTask(AndroidTask<PrepareDependenciesTask> prepareDependenciesTask);

    AndroidTask<ProcessAndroidResources> getGenerateRClassTask();

    void setGenerateRClassTask(AndroidTask<ProcessAndroidResources> generateRClassTask);

    AndroidTask<Task> getSourceGenTask();

    void setSourceGenTask(AndroidTask<Task> sourceGenTask);

    AndroidTask<Task> getResourceGenTask();

    void setResourceGenTask(AndroidTask<Task> resourceGenTask);

    AndroidTask<Task> getAssetGenTask();

    void setAssetGenTask(AndroidTask<Task> assetGenTask);

    AndroidTask<CheckManifest> getCheckManifestTask();

    void setCheckManifestTask(AndroidTask<CheckManifest> checkManifestTask);

    AndroidTask<RenderscriptCompile> getRenderscriptCompileTask();

    void setRenderscriptCompileTask(AndroidTask<RenderscriptCompile> renderscriptCompileTask);

    AndroidTask<AidlCompile> getAidlCompileTask();

    void setAidlCompileTask(AndroidTask<AidlCompile> aidlCompileTask);

    AndroidTask<ShaderCompile> getShaderCompileTask();

    void setShaderCompileTask(AndroidTask<ShaderCompile> shaderCompileTask);

    @Nullable
    AndroidTask<MergeResources> getMergeResourcesTask();

    void setMergeResourcesTask(@Nullable AndroidTask<MergeResources> mergeResourcesTask);

    @Nullable
    AndroidTask<MergeSourceSetFolders> getMergeAssetsTask();

    void setMergeAssetsTask(@Nullable AndroidTask<MergeSourceSetFolders> mergeAssetsTask);

    @Nullable
    AndroidTask<MergeSourceSetFolders> getMergeJniLibFoldersTask();

    void setMergeJniLibFoldersTask(@Nullable AndroidTask<MergeSourceSetFolders> mergeJniLibsTask);

    AndroidTask<GenerateBuildConfig> getGenerateBuildConfigTask();

    void setGenerateBuildConfigTask(AndroidTask<GenerateBuildConfig> generateBuildConfigTask);

    AndroidTask<GenerateResValues> getGenerateResValuesTask();

    void setGenerateResValuesTask(AndroidTask<GenerateResValues> generateResValuesTask);

    @Nullable
    AndroidTask<DataBindingExportBuildInfoTask> getDataBindingExportInfoTask();

    void setDataBindingExportInfoTask(
            @Nullable AndroidTask<DataBindingExportBuildInfoTask> dataBindingExportInfoTask);

    @Nullable
    AndroidTask<DataBindingProcessLayoutsTask> getDataBindingProcessLayoutsTask();

    void setDataBindingProcessLayoutsTask(
            @Nullable AndroidTask<DataBindingProcessLayoutsTask> dataBindingProcessLayoutsTask);

    AndroidTask<Sync> getProcessJavaResourcesTask();

    void setProcessJavaResourcesTask(AndroidTask<Sync> processJavaResourcesTask);

    void setMergeJavaResourcesTask(AndroidTask<TransformTask> mergeJavaResourcesTask);

    AndroidTask<TransformTask> getMergeJavaResourcesTask();

    @Nullable
    AndroidTask<? extends Task> getJavaCompilerTask();

    @Nullable
    AndroidTask<? extends JavaCompile> getJavacTask();

    void setJavacTask(@Nullable AndroidTask<? extends JavaCompile> javacTask);

    void setJavaCompilerTask(@NonNull AndroidTask<? extends Task> javaCompileTask);

    AndroidTask<Task> getCompileTask();
    void setCompileTask(AndroidTask<Task> compileTask);

    AndroidTask<GenerateApkDataTask> getMicroApkTask();
    void setMicroApkTask(AndroidTask<GenerateApkDataTask> microApkTask);

    AndroidTask<?> getCoverageReportTask();

    void setCoverageReportTask(AndroidTask<?> coverageReportTask);

    @Nullable
    AndroidTask<ExternalNativeBuildTask> getExternalNativeBuildTask();
    void setExternalNativeBuildTask(@NonNull AndroidTask<ExternalNativeBuildTask> task);

    @Nullable
    ExternalNativeJsonGenerator getExternalNativeJsonGenerator();
    void setExternalNativeJsonGenerator(@NonNull ExternalNativeJsonGenerator generator);

    @NonNull
    Collection<NativeBuildConfigValue> getExternalNativeBuildConfigValues();
    void addExternalNativeBuildConfigValues(@NonNull Collection<NativeBuildConfigValue> values);

    @Nullable
    InstantRunTaskManager getInstantRunTaskManager();
    void setInstantRunTaskManager(InstantRunTaskManager taskManager);
}
