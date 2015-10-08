/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.ApiVersion;
import com.android.manifmerger.ManifestMerger2;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A task that processes the manifest
 */
@ParallelizableTask
public class MergeManifests extends ManifestProcessorTask {

    private String minSdkVersion;
    private String targetSdkVersion;
    private Integer maxSdkVersion;
    private File reportFile;
    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;
    private ApkVariantOutputData variantOutputData;
    private List<ManifestDependencyImpl> libraries;

    @Override
    protected void doFullTaskAction() {
        getBuilder().mergeManifests(
                getMainManifest(),
                getManifestOverlays(),
                getLibraries(),
                getPackageOverride(),
                getVersionCode(),
                getVersionName(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getMaxSdkVersion(),
                getManifestOutputFile().getAbsolutePath(),
                // no appt friendly merged manifest file necessary for applications.
                null /* aaptFriendlyManifestOutputFile */,
                ManifestMerger2.MergeType.APPLICATION,
                variantConfiguration.getManifestPlaceholders(),
                getReportFile());
    }

    @InputFile
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @InputFiles
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getIdOverride();
    }

    @Input
    public int getVersionCode() {
        if (variantOutputData != null) {
            return variantOutputData.getVersionCode();
        }
        return variantConfiguration.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        if (variantOutputData != null) {
            return variantOutputData.getVersionName();
        }
        return variantConfiguration.getVersionName();
    }

    /**
     * Returns a serialized version of our map of key value pairs for placeholder substitution.
     *
     * This serialized form is only used by gradle to compare past and present tasks to determine
     * whether a task need to be re-run or not.
     */
    @SuppressWarnings("unused")
    @Input
    @Optional
    public String getManifestPlaceholders() {
        return serializeMap(variantConfiguration.getManifestPlaceholders());
    }

    /**
     * A synthetic input to allow gradle up-to-date checks to work.
     *
     * Since List<ManifestDependencyImpl> can't be used directly, as @Nested doesn't work on lists,
     * this method gathers and returns the underlying manifest files.
     */
    @SuppressWarnings("unused")
    @InputFiles
    List<File> getLibraryManifests() {
        List<ManifestDependencyImpl> libs = getLibraries();
        if (libs == null || libs.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> files = Lists.newArrayListWithCapacity(libs.size() * 2);
        for (ManifestDependencyImpl mdi : libs) {
            files.addAll(mdi.getAllManifests());
        }

        return files;
    }


    @Input
    @Optional
    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @Input
    @Optional
    public String getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(String targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @Input
    @Optional
    public Integer getMaxSdkVersion() {
        return maxSdkVersion;
    }

    public void setMaxSdkVersion(Integer maxSdkVersion) {
        this.maxSdkVersion = maxSdkVersion;
    }

    @OutputFile
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    public ApkVariantOutputData getVariantOutputData() {
        return variantOutputData;
    }

    public void setVariantOutputData(ApkVariantOutputData variantOutputData) {
        this.variantOutputData = variantOutputData;
    }

    public List<ManifestDependencyImpl> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<ManifestDependencyImpl> libraries) {
        this.libraries = libraries;
    }

    public static class ConfigAction implements TaskConfigAction<MergeManifests> {

        VariantOutputScope scope;

        public ConfigAction(VariantOutputScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<MergeManifests> getType() {
            return MergeManifests.class;
        }

        @Override
        public void execute(MergeManifests processManifestTask) {
            BaseVariantOutputData variantOutputData = scope.getVariantOutputData();

            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    variantData.getVariantConfiguration();

            variantOutputData.manifestProcessorTask = processManifestTask;

            processManifestTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processManifestTask.setVariantName(config.getFullName());

            processManifestTask.dependsOn(variantData.prepareDependenciesTask);
            if (variantData.generateApkDataTask != null) {
                processManifestTask.dependsOn(variantData.generateApkDataTask);
            }
            if (scope.getCompatibleScreensManifestTask() != null) {
                processManifestTask.dependsOn(scope.getCompatibleScreensManifestTask().getName());
            }

            processManifestTask.setVariantConfiguration(config);
            if (variantOutputData instanceof ApkVariantOutputData) {
                processManifestTask.variantOutputData =
                        (ApkVariantOutputData) variantOutputData;
            }

            ConventionMappingHelper.map(processManifestTask, "libraries",
                    new Callable<List<ManifestDependencyImpl>>() {
                        @Override
                        public List<ManifestDependencyImpl> call() throws Exception {
                            List<ManifestDependencyImpl> manifests =
                                    getManifestDependencies(config.getDirectLibraries());

                            if (variantData.generateApkDataTask != null &&
                                    variantData.getVariantConfiguration().getBuildType().
                                            isEmbedMicroApp()) {
                                manifests.add(new ManifestDependencyImpl(
                                        variantData.generateApkDataTask.getManifestFile(),
                                        Collections.<ManifestDependencyImpl>emptyList()));
                            }

                            if (scope.getCompatibleScreensManifestTask() != null) {
                                manifests.add(new ManifestDependencyImpl(
                                        scope.getCompatibleScreensManifestFile(),
                                        Collections.<ManifestDependencyImpl>emptyList()));
                            }

                            return manifests;
                        }
                    });

            ConventionMappingHelper.map(processManifestTask, "minSdkVersion",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                                return scope.getGlobalScope().getAndroidBuilder()
                                        .getTargetCodename();
                            }

                            ApiVersion minSdk = config.getMergedFlavor().getMinSdkVersion();
                            return minSdk == null ? null : minSdk.getApiString();
                        }
                    });

            ConventionMappingHelper.map(processManifestTask, "targetSdkVersion",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                                return scope.getGlobalScope().getAndroidBuilder()
                                        .getTargetCodename();
                            }
                            ApiVersion targetSdk = config.getMergedFlavor().getTargetSdkVersion();
                            return targetSdk == null ? null : targetSdk.getApiString();
                        }
                    });

            ConventionMappingHelper.map(processManifestTask, "maxSdkVersion",
                    new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                                return null;
                            }
                            return config.getMergedFlavor().getMaxSdkVersion();
                        }
                    });

            processManifestTask.setManifestOutputFile(scope.getManifestOutputFile());

            processManifestTask.setReportFile(scope.getVariantScope().getManifestReportFile());

        }

        @NonNull
        private static List<ManifestDependencyImpl> getManifestDependencies(
                List<LibraryDependency> libraries) {

            List<ManifestDependencyImpl> list = Lists.newArrayListWithCapacity(libraries.size());

            for (LibraryDependency lib : libraries) {
                if (!lib.isOptional()) {
                    // get the dependencies
                    List<ManifestDependencyImpl> children =
                            getManifestDependencies(lib.getDependencies());
                    list.add(new ManifestDependencyImpl(lib.getName(), lib.getManifest(), children));
                }
            }

            return list;
        }
    }
}
