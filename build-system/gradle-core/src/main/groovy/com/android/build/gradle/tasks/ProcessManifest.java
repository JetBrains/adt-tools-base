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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.dependency.ManifestDependency;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.manifmerger.ManifestMerger2;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * a Task that only merge a single manifest with its overlays.
 */
@ParallelizableTask
public class ProcessManifest extends ManifestProcessorTask {

    private String minSdkVersion;

    private String targetSdkVersion;

    private Integer maxSdkVersion;

    private VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor>
            variantConfiguration;

    private File reportFile;

    @Override
    protected void doFullTaskAction() {
        File aaptManifestFile = getAaptFriendlyManifestOutputFile();
        String aaptFriendlyManifestOutputFilePath =
                aaptManifestFile == null ? null : aaptManifestFile.getAbsolutePath();
        getBuilder().mergeManifests(
                getMainManifest(),
                getManifestOverlays(),
                Collections.<ManifestDependency>emptyList(),
                getPackageOverride(),
                getVersionCode(),
                getVersionName(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getMaxSdkVersion(),
                getManifestOutputFile().getAbsolutePath(),
                aaptFriendlyManifestOutputFilePath,
                ManifestMerger2.MergeType.LIBRARY,
                variantConfiguration.getManifestPlaceholders(),
                getReportFile());
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

    public VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantConfiguration(
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
    }

    @Input
    @Optional
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    @InputFile
    public File getMainManifest() {
        return variantConfiguration.getMainManifest();
    }

    @Input
    @Optional
    public String getPackageOverride() {
        return variantConfiguration.getApplicationId();
    }

    @Input
    public int getVersionCode() {
        return variantConfiguration.getVersionCode();
    }

    @Input
    @Optional
    public String getVersionName() {
        return variantConfiguration.getVersionName();
    }

    @InputFiles
    public List<File> getManifestOverlays() {
        return variantConfiguration.getManifestOverlays();
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

    public static class ConfigAction implements TaskConfigAction<ProcessManifest> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Manifest");
        }

        @NonNull
        @Override
        public Class<ProcessManifest> getType() {
            return ProcessManifest.class;
        }

        @Override
        public void execute(@NonNull ProcessManifest processManifest) {
            VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();
            final AndroidBuilder androidBuilder = scope.getGlobalScope().getAndroidBuilder();

            // get single output for now.
            BaseVariantOutputData variantOutputData = scope.getVariantData().getOutputs().get(0);

            variantOutputData.manifestProcessorTask = processManifest;
            processManifest.setAndroidBuilder(androidBuilder);
            processManifest.setVariantName(config.getFullName());

            processManifest.variantConfiguration = config;

            final ProductFlavor mergedFlavor = config.getMergedFlavor();

            ConventionMappingHelper.map(processManifest, "minSdkVersion", new Callable<String>() {
                @Override
                public String call() throws Exception {
                    if (androidBuilder.isPreviewTarget()) {
                        return androidBuilder.getTargetCodename();
                    }
                    ApiVersion minSdkVersion = mergedFlavor.getMinSdkVersion();
                    if (minSdkVersion == null) {
                        return null;
                    }
                    return minSdkVersion.getApiString();
                }
            });

            ConventionMappingHelper.map(processManifest, "targetSdkVersion",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (androidBuilder.isPreviewTarget()) {
                                return androidBuilder.getTargetCodename();
                            }
                            ApiVersion targetSdkVersion = mergedFlavor.getTargetSdkVersion();
                            if (targetSdkVersion == null) {
                                return null;
                            }
                            return targetSdkVersion.getApiString();
                        }
                    });

            ConventionMappingHelper.map(processManifest, "maxSdkVersion", new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    if (androidBuilder.isPreviewTarget()) {
                        return null;
                    } else {
                        return mergedFlavor.getMaxSdkVersion();
                    }
                }
            });

            processManifest.setManifestOutputFile(
                    variantOutputData.getScope().getManifestOutputFile());

            processManifest.setAaptFriendlyManifestOutputFile(
                    scope.getAaptFriendlyManifestOutputFile());

        }
    }
}
