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

import static com.android.build.gradle.internal.TaskManager.DIR_BUNDLES;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.BinaryFileProviderTask;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.JackTask;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.core.VariantType;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScopeImpl implements VariantScope {

    @NonNull
    private GlobalScope globalScope;
    @NonNull
    private BaseVariantData<? extends BaseVariantOutputData> variantData;
    @NonNull
    private TransformManager transformManager;
    @Nullable
    private Collection<Object> ndkBuildable;
    @Nullable
    private Collection<File> ndkSoFolder;
    @Nullable
    private File ndkObjFolder;
    @NonNull
    private Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @Nullable
    private File mergeResourceOutputDir;

    // Tasks
    private AndroidTask<Task> preBuildTask;
    private AndroidTask<PrepareDependenciesTask> prepareDependenciesTask;
    private AndroidTask<ProcessAndroidResources> generateRClassTask;

    private AndroidTask<Task> sourceGenTask;
    private AndroidTask<Task> resourceGenTask;
    private AndroidTask<Task> assetGenTask;
    private AndroidTask<CheckManifest> checkManifestTask;

    private AndroidTask<RenderscriptCompile> renderscriptCompileTask;
    private AndroidTask<AidlCompile> aidlCompileTask;
    @Nullable
    private AndroidTask<MergeResources> mergeResourcesTask;
    @Nullable
    private AndroidTask<MergeSourceSetFolders> mergeAssetsTask;
    private AndroidTask<GenerateBuildConfig> generateBuildConfigTask;
    private AndroidTask<GenerateResValues> generateResValuesTask;

    private AndroidTask<Sync> processJavaResourcesTask;
    private AndroidTask<TransformTask> mergeJavaResourcesTask;

    private AndroidTask<MergeSourceSetFolders> mergeJniLibsFolderTask;

    private AndroidTask<NdkCompile> ndkCompileTask;

    @Nullable
    private AndroidTask<DataBindingExportBuildInfoTask> dataBindingExportInfoTask;
    @Nullable
    private AndroidTask<DataBindingProcessLayoutsTask> dataBindingProcessLayoutsTask;

    /** @see BaseVariantData#javaCompilerTask */
    @Nullable
    private AndroidTask<? extends AbstractCompile> javaCompilerTask;
    @Nullable
    private AndroidTask<JavaCompile> javacTask;
    @Nullable
    private AndroidTask<JackTask> jackTask;

    // empty anchor compile task to set all compilations tasks as dependents.
    private AndroidTask<Task> compileTask;

    /**
     * This is an instance of {@link JacocoReportTask} in android test variants, an umbrella
     * {@link Task} in app and lib variants and null in unit test variants.
     */
    private AndroidTask<?> coverageReportTask;

    private FileSupplier mappingFileProviderTask;
    private AndroidTask<BinaryFileProviderTask> binayFileProviderTask;

    private File resourceOutputDir;


    public VariantScopeImpl(
            @NonNull GlobalScope globalScope,
            @NonNull TransformManager transformManager,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        this.globalScope = globalScope;
        this.transformManager = transformManager;
        this.variantData = variantData;
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    @NonNull
    public BaseVariantData<? extends BaseVariantOutputData> getVariantData() {
        return variantData;
    }

    @Override
    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantData.getVariantConfiguration();
    }

    @NonNull
    @Override
    public String getDirName() {
        return variantData.getVariantConfiguration().getDirName();
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        return variantData.getVariantConfiguration().getDirectorySegments();
    }

    @NonNull
    @Override
    public TransformManager getTransformManager() {
        return transformManager;
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return prefix + StringHelper.capitalize(getVariantConfiguration().getFullName()) + suffix;
    }

    @Override
    @Nullable
    public Collection<Object> getNdkBuildable() {
        return ndkBuildable;
    }

    @Override
    public void setNdkBuildable(@NonNull Collection<Object> ndkBuildable) {
        this.ndkBuildable = ndkBuildable;
    }

    @Nullable
    @Override
    public AndroidTask<DataBindingExportBuildInfoTask> getDataBindingExportInfoTask() {
        return dataBindingExportInfoTask;
    }

    @Override
    public void setDataBindingExportInfoTask(
            @Nullable AndroidTask<DataBindingExportBuildInfoTask> dataBindingExportInfoTask) {
        this.dataBindingExportInfoTask = dataBindingExportInfoTask;
    }

    @Nullable
    @Override
    public AndroidTask<DataBindingProcessLayoutsTask> getDataBindingProcessLayoutsTask() {
        return dataBindingProcessLayoutsTask;
    }

    @Override
    public void setDataBindingProcessLayoutsTask(
            @Nullable AndroidTask<DataBindingProcessLayoutsTask> dataBindingProcessLayoutsTask) {
        this.dataBindingProcessLayoutsTask = dataBindingProcessLayoutsTask;
    }

    @Override
    @Nullable
    public Collection<File> getNdkSoFolder() {
        return ndkSoFolder;
    }

    @Override
    public void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder) {
        this.ndkSoFolder = ndkSoFolder;
    }

    @Override
    @Nullable
    public File getNdkObjFolder() {
        return ndkObjFolder;
    }

    @Override
    public void setNdkObjFolder(@NonNull File ndkObjFolder) {
        this.ndkObjFolder = ndkObjFolder;
    }

    /**
     * Return the folder containing the shared object with debugging symbol for the specified ABI.
     */
    @Override
    @Nullable
    public File getNdkDebuggableLibraryFolders(@NonNull Abi abi) {
        return ndkDebuggableLibraryFolders.get(abi);
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    @Override
    @Nullable
    public BaseVariantData getTestedVariantData() {
        return variantData instanceof TestVariantData ?
                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData() :
                null;
    }

    // Precomputed file paths.

    @Override
    @NonNull
    public FileCollection getJavaClasspath() {
        return getGlobalScope().getProject().files(
                getGlobalScope().getAndroidBuilder().getCompileClasspath(
                        getVariantData().getVariantConfiguration()));
    }

    @Override
    @NonNull
    public File getJavaOutputDir() {
        return new File(globalScope.getIntermediatesDir(), "/classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public Iterable<File> getJavaOuptuts() {
        return Iterables.concat(
                getJavaClasspath(),
                ImmutableList.of(
                        getJavaOutputDir(),
                        getJavaDependencyCache()));
    }

    @Override
    @NonNull
    public File getJavaDependencyCache() {
        return new File(globalScope.getIntermediatesDir(), "/dependency-cache/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getPreDexOutputDir() {
        return new File(globalScope.getIntermediatesDir(), "/pre-dexed/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProguardOutputFile() {
        return (variantData instanceof LibraryVariantData) ?
                new File(globalScope.getIntermediatesDir(),
                        DIR_BUNDLES + "/" + getVariantConfiguration().getDirName()
                                + "/classes.jar") :
                new File(globalScope.getIntermediatesDir(),
                        "/classes-proguard/" + getVariantConfiguration().getDirName()
                                + "/classes.jar");
    }

    @Override
    @NonNull
    public File getProguardComponentsJarFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/componentClasses.jar");
    }

    @Override
    @NonNull
    public File getJarMergingOutputFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/allclasses.jar");
    }

    @Override
    @NonNull
    public File getManifestKeepListFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/manifest_keep.txt");
    }

    @Override
    @NonNull
    public File getMainDexListFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/maindexlist.txt");
    }

    @Override
    @NonNull
    public File getRenderscriptSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/rs/" + variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRenderscriptLibOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "rs/" + variantData.getVariantConfiguration().getDirName() + "/lib");
    }

    @Override
    @NonNull
    public File getSymbolLocation() {
        return new File(globalScope.getIntermediatesDir() + "/symbols/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getFinalResourcesDir() {
        return Objects.firstNonNull(resourceOutputDir, getDefaultMergeResourcesOutputDir());
    }

    @Override
    public void setResourceOutputDir(@NonNull File resourceOutputDir) {
        this.resourceOutputDir = resourceOutputDir;
    }

    @Override
    @NonNull
    public File getDefaultMergeResourcesOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "/res/merged/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getMergeResourcesOutputDir() {
        if (mergeResourceOutputDir == null) {
            return getDefaultMergeResourcesOutputDir();
        }
        return mergeResourceOutputDir;
    }

    @Override
    public void setMergeResourceOutputDir(@Nullable File mergeResourceOutputDir) {
        this.mergeResourceOutputDir = mergeResourceOutputDir;
    }

    @NonNull
    @Override
    public File getResourceBlameLogDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame", "res", getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getMergeAssetsOutputDir() {
        return getVariantConfiguration().getType() == VariantType.LIBRARY ?
                new File(globalScope.getIntermediatesDir(),
                        DIR_BUNDLES + "/" + getVariantConfiguration().getDirName() +
                                "/assets") :
                new File(globalScope.getIntermediatesDir(),
                        "/assets/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMergeNativeLibsOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/jniLibs/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildConfigSourceOutputDir() {
        return new File(globalScope.getBuildDir() + "/"  + FD_GENERATED + "/source/buildConfig/"
                + variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getGeneratedResourcesDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getGeneratedResOutputDir() {
        return getGeneratedResourcesDir("resValues");
    }

    @Override
    @NonNull
    public File getGeneratedPngsOutputDir() {
        return getGeneratedResourcesDir("pngs");
    }

    @Override
    @NonNull
    public File getRenderscriptResOutputDir() {
        return getGeneratedResourcesDir("rs");
    }

    @Override
    @NonNull
    public File getPackagedJarsJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "packagedJarsJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getSourceFoldersJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "sourceFolderJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getJavaResourcesDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "javaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRClassSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/r/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getAidlSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/aidl/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getIncrementalDir(String name) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "incremental",
                name);
    }

    @Override
    @NonNull
    public File getPackagedAidlDir() {
        return new File(globalScope.getIntermediatesDir(),
                DIR_BUNDLES + "/" + getVariantConfiguration().getDirName() + "/aidl");
    }

    @Override
    @NonNull
    public File getJillPackagedLibrariesDir() {
        return new File(globalScope.getIntermediatesDir(),
                "jill/" + getVariantConfiguration().getDirName() + "/packaged");
    }

    @Override
    @NonNull
    public File getJillRuntimeLibrariesDir() {
        return new File(globalScope.getIntermediatesDir(),
                "jill/" + getVariantConfiguration().getDirName() + "/runtime");
    }

    @Override
    @NonNull
    public File getJackDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "dex/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getJackClassesZip() {
        return new File(globalScope.getIntermediatesDir(),
                "packaged/" + getVariantConfiguration().getDirName() + "/classes.zip");
    }

    @Override
    @NonNull
    public File getClassOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir(),
                "dataBindingInfo/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutInfoOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-info/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getLayoutFolderOutputForDataBinding() {
        return new File(globalScope.getIntermediatesDir() + "/data-binding-layout-out/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getGeneratedClassListOutputFileForDataBinding() {
        return new File(getLayoutInfoOutputForDataBinding(), "_generated.txt");
    }

    @Override
    @NonNull
    public File getProguardOutputFolder() {
        return new File(globalScope.getBuildDir(), "/" + FD_OUTPUTS + "/mapping/" +
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProcessAndroidResourcesProguardOutputFile() {
        return new File(globalScope.getIntermediatesDir(),
                "/proguard-rules/" + getVariantConfiguration().getDirName() + "/aapt_rules.txt");
    }

    @Override
    public File getMappingFile() {
        return new File(globalScope.getOutputsDir(),
                "/mapping/" + getVariantConfiguration().getDirName() + "/mapping.txt");
    }

    @NonNull
    @Override
    public File getAaptFriendlyManifestOutputFile() {
            return FileUtils.join(globalScope.getIntermediatesDir(), DIR_BUNDLES,
                    getVariantConfiguration().getDirName(), "aapt", "AndroidManifest.xml");
    }

    @NonNull
    @Override
    public File getManifestReportFile() {
        return FileUtils.join(getGlobalScope().getOutputsDir(),
                "logs", "manifest-merger-" + variantData.getVariantConfiguration().getBaseName()
                        + "-report.txt");
    }

    // Tasks getters/setters.

    @Override
    public AndroidTask<Task> getPreBuildTask() {
        return preBuildTask;
    }

    @Override
    public void setPreBuildTask(
            AndroidTask<Task> preBuildTask) {
        this.preBuildTask = preBuildTask;
    }

    @Override
    public AndroidTask<PrepareDependenciesTask> getPrepareDependenciesTask() {
        return prepareDependenciesTask;
    }

    @Override
    public void setPrepareDependenciesTask(
            AndroidTask<PrepareDependenciesTask> prepareDependenciesTask) {
        this.prepareDependenciesTask = prepareDependenciesTask;
    }

    @Override
    public AndroidTask<ProcessAndroidResources> getGenerateRClassTask() {
        return generateRClassTask;
    }

    @Override
    public void setGenerateRClassTask(
            AndroidTask<ProcessAndroidResources> generateRClassTask) {
        this.generateRClassTask = generateRClassTask;
    }

    @Override
    public AndroidTask<Task> getSourceGenTask() {
        return sourceGenTask;
    }

    @Override
    public void setSourceGenTask(
            AndroidTask<Task> sourceGenTask) {
        this.sourceGenTask = sourceGenTask;
    }

    @Override
    public AndroidTask<Task> getResourceGenTask() {
        return resourceGenTask;
    }

    @Override
    public void setResourceGenTask(
            AndroidTask<Task> resourceGenTask) {
        this.resourceGenTask = resourceGenTask;
    }

    @Override
    public AndroidTask<Task> getAssetGenTask() {
        return assetGenTask;
    }

    @Override
    public void setAssetGenTask(
            AndroidTask<Task> assetGenTask) {
        this.assetGenTask = assetGenTask;
    }

    @Override
    public AndroidTask<CheckManifest> getCheckManifestTask() {
        return checkManifestTask;
    }

    @Override
    public void setCheckManifestTask(
            AndroidTask<CheckManifest> checkManifestTask) {
        this.checkManifestTask = checkManifestTask;
    }

    @Override
    public AndroidTask<RenderscriptCompile> getRenderscriptCompileTask() {
        return renderscriptCompileTask;
    }

    @Override
    public void setRenderscriptCompileTask(
            AndroidTask<RenderscriptCompile> renderscriptCompileTask) {
        this.renderscriptCompileTask = renderscriptCompileTask;
    }

    @Override
    public AndroidTask<AidlCompile> getAidlCompileTask() {
        return aidlCompileTask;
    }

    @Override
    public void setAidlCompileTask(
            AndroidTask<AidlCompile> aidlCompileTask) {
        this.aidlCompileTask = aidlCompileTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeResources> getMergeResourcesTask() {
        return mergeResourcesTask;
    }

    @Override
    public void setMergeResourcesTask(
            @Nullable AndroidTask<MergeResources> mergeResourcesTask) {
        this.mergeResourcesTask = mergeResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<MergeSourceSetFolders> getMergeAssetsTask() {
        return mergeAssetsTask;
    }

    @Override
    public void setMergeAssetsTask(
            @Nullable AndroidTask<MergeSourceSetFolders> mergeAssetsTask) {
        this.mergeAssetsTask = mergeAssetsTask;
    }

    @Nullable
    @Override
    public AndroidTask<MergeSourceSetFolders> getMergeJniLibFoldersTask() {
        return mergeJniLibsFolderTask;
    }

    @Override
    public void setMergeJniLibFoldersTask(
            @Nullable AndroidTask<MergeSourceSetFolders> mergeJniLibsFolderTask) {
        this.mergeJniLibsFolderTask = mergeJniLibsFolderTask;
    }

    @Override
    public AndroidTask<GenerateBuildConfig> getGenerateBuildConfigTask() {
        return generateBuildConfigTask;
    }

    @Override
    public void setGenerateBuildConfigTask(
            AndroidTask<GenerateBuildConfig> generateBuildConfigTask) {
        this.generateBuildConfigTask = generateBuildConfigTask;
    }

    @Override
    public AndroidTask<GenerateResValues> getGenerateResValuesTask() {
        return generateResValuesTask;
    }

    @Override
    public void setGenerateResValuesTask(
            AndroidTask<GenerateResValues> generateResValuesTask) {
        this.generateResValuesTask = generateResValuesTask;
    }

    @Override
    public AndroidTask<Sync> getProcessJavaResourcesTask() {
        return processJavaResourcesTask;
    }

    @Override
    public void setProcessJavaResourcesTask(
            AndroidTask<Sync> processJavaResourcesTask) {
        this.processJavaResourcesTask = processJavaResourcesTask;
    }

    @Override
    public void setMergeJavaResourcesTask(
            AndroidTask<TransformTask> mergeJavaResourcesTask) {
        this.mergeJavaResourcesTask = mergeJavaResourcesTask;
    }

    /**
     * Returns the task extracting java resources from libraries and merging those with java
     * resources coming from the variant's source folders.
     * @return the task merging resources.
     */
    @Override
    public AndroidTask<TransformTask> getMergeJavaResourcesTask() {
        return mergeJavaResourcesTask;
    }

    @Override
    @Nullable
    public AndroidTask<? extends AbstractCompile> getJavaCompilerTask() {
        return javaCompilerTask;
    }

    @Override
    @Nullable
    public AndroidTask<JackTask> getJackTask() {
        return jackTask;
    }

    @Override
    public void setJackTask(
            @Nullable AndroidTask<JackTask> jackTask) {
        this.jackTask = jackTask;
    }

    @Override
    @Nullable
    public AndroidTask<JavaCompile> getJavacTask() {
        return javacTask;
    }

    @Override
    public void setJavacTask(
            @Nullable AndroidTask<JavaCompile> javacTask) {
        this.javacTask = javacTask;
    }

    @Override
    public void setJavaCompilerTask(
            @NonNull AndroidTask<? extends AbstractCompile> javaCompileTask) {
        this.javaCompilerTask = javaCompileTask;
    }

    @Override
    public AndroidTask<Task> getCompileTask() {
        return compileTask;
    }

    @Override
    public void setCompileTask(
            AndroidTask<Task> compileTask) {
        this.compileTask = compileTask;
    }

    @Override
    public AndroidTask<?> getCoverageReportTask() {
        return coverageReportTask;
    }

    @Override
    public void setCoverageReportTask(AndroidTask<?> coverageReportTask) {
        this.coverageReportTask = coverageReportTask;
    }
}
