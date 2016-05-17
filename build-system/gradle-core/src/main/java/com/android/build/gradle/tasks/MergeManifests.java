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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestMerger2.Invoker.Feature;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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
    private List<AndroidLibrary> libraries;
    private List<Feature> optionalFeatures;

    @Override
    protected void doFullTaskAction() {
        getBuilder().mergeManifestsForApplication(
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
                // no aapt friendly merged manifest file necessary for applications.
                null /* aaptFriendlyManifestOutputFile */,
                getInstantRunManifestOutputFile().getAbsolutePath(),
                ManifestMerger2.MergeType.APPLICATION,
                variantConfiguration.getManifestPlaceholders(),
                getOptionalFeatures(),
                getReportFile());
    }

    @Optional
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
     * Since List<AndroidLibrary> can't be used directly, as @Nested doesn't work on lists,
     * this method gathers and returns the underlying manifest files.
     */
    @SuppressWarnings("unused")
    @InputFiles
    List<File> getLibraryManifests() {
        List<AndroidLibrary> libs = getLibraries();
        if (libs == null || libs.isEmpty()) {
            return Collections.emptyList();
        }

        // this is a graph of Android Library so need to get them recursively.
        List<File> files = Lists.newArrayListWithCapacity(libs.size() * 2);
        for (AndroidLibrary androidLibrary : libs) {
            fillManifestList(androidLibrary, files);
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

    /** Not an input, see {@link #getOptionalFeaturesString()}. */
    public List<Feature> getOptionalFeatures() {
        return optionalFeatures;
    }

    /** Synthetic input for {@link #getOptionalFeatures()} */
    @SuppressWarnings("unused")
    @Input
    public List<String> getOptionalFeaturesString() {
        return optionalFeatures.stream().map(Enum::toString).collect(Collectors.toList());
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

    public List<AndroidLibrary> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<AndroidLibrary> libraries) {
        this.libraries = libraries;
    }

    public static class ConfigAction implements TaskConfigAction<MergeManifests> {

        private final VariantOutputScope scope;
        private final List<Feature> optionalFeatures;

        public ConfigAction(VariantOutputScope scope, List<Feature> optionalFeatures) {
            this.scope = scope;
            this.optionalFeatures = optionalFeatures;
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
        public void execute(@NonNull MergeManifests processManifestTask) {
            BaseVariantOutputData variantOutputData = scope.getVariantOutputData();

            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    variantData.getVariantConfiguration();

            variantOutputData.manifestProcessorTask = processManifestTask;

            processManifestTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processManifestTask.setVariantName(config.getFullName());

            processManifestTask.setVariantConfiguration(config);
            if (variantOutputData instanceof ApkVariantOutputData) {
                processManifestTask.variantOutputData =
                        (ApkVariantOutputData) variantOutputData;
            }

            ConventionMappingHelper.map(processManifestTask, "libraries",
                    new Callable<List<AndroidLibrary>>() {
                        @Override
                        public List<AndroidLibrary> call() throws Exception {
                            List<AndroidLibrary> manifests = Lists.newArrayList(
                                    config.getCompileAndroidLibraries());

                            if (scope.getVariantScope().getMicroApkTask() != null &&
                                    variantData.getVariantConfiguration().getBuildType().
                                            isEmbedMicroApp()) {
                                manifests.add(new ManifestOnlyLibrary(
                                        scope.getVariantScope().getMicroApkManifestFile(),
                                        "Wear App sub-manifest"));
                            }

                            if (scope.getCompatibleScreensManifestTask() != null) {
                                manifests.add(new ManifestOnlyLibrary(
                                        scope.getCompatibleScreensManifestFile(),
                                        "Compatible-Screens sub-manifest"));
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
            processManifestTask.setInstantRunManifestOutputFile(
                    scope.getVariantScope().getInstantRunManifestOutputFile());

            processManifestTask.setReportFile(scope.getVariantScope().getManifestReportFile());
            processManifestTask.optionalFeatures = optionalFeatures;

        }

        /**
         * Implementation of AndroidLibrary that only contains a manifest.
         *
         * This is used to pass to the merger manifest snippet that needs to be added during
         * merge.
         */
        private static class ManifestOnlyLibrary implements AndroidLibrary {

            @NonNull
            private final File manifest;

            @NonNull
            private final String name;

            public ManifestOnlyLibrary(@NonNull File manifest, @NonNull String name) {
                this.manifest = manifest;
                this.name = name;
            }

            @NonNull
            @Override
            public File getManifest() {
                return manifest;
            }

            @NonNull
            @Override
            public String getName() {
                return name;
            }

            @NonNull
            @Override
            public List<? extends AndroidLibrary> getLibraryDependencies() {
                return ImmutableList.of();
            }

            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public boolean isProvided() {
                return false;
            }

            @Override
            public boolean isSkipped() {
                return false;
            }

            // --------

            @Nullable
            @Override
            public String getProject() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @Nullable
            @Override
            public String getProjectVariant() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getBundle() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public Collection<File> getLocalJars() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public Collection<? extends JavaLibrary> getJavaDependencies() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getJarFile() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getResFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getAssetsFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getJniFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getAidlFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getRenderscriptFolder() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getProguardRules() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getLintJar() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getExternalAnnotations() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getPublicResources() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public File getSymbolFile() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @Nullable
            @Override
            public MavenCoordinates getRequestedCoordinates() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }

            @NonNull
            @Override
            public MavenCoordinates getResolvedCoordinates() {
                throw new UnsupportedOperationException("ManifestOnlyLibrary can only be queried for the Manifest file.");
            }
        }
    }
}
