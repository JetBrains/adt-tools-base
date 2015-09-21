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
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.dependency.ManifestDependencyImpl;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A task that processes the manifest
 */
@ParallelizableTask
public class ProcessTestManifest extends ManifestProcessorTask {

    private File testManifestFile;
    private File tmpDir;
    private String testApplicationId;
    private String minSdkVersion;
    private String targetSdkVersion;
    private String testedApplicationId;
    private String instrumentationRunner;
    private Boolean handleProfiling;
    private Boolean functionalTest;
    private Map<String, Object> placeholdersValues;
    private List<ManifestDependencyImpl> libraries;

    @Override
    protected void doFullTaskAction() throws IOException {
        getBuilder().processTestManifest(
                getTestApplicationId(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getTestedApplicationId(),
                getInstrumentationRunner(),
                getHandleProfiling(),
                getFunctionalTest(),
                getTestManifestFile(),
                getLibraries(),
                getPlaceholdersValues(),
                getManifestOutputFile(),
                getTmpDir());
    }

    @InputFile
    @Optional
    public File getTestManifestFile() {
        return testManifestFile;
    }

    public void setTestManifestFile(File testManifestFile) {
        this.testManifestFile = testManifestFile;
    }

    public File getTmpDir() {
        return tmpDir;
    }

    public void setTmpDir(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    @Input
    public String getTestApplicationId() {
        return testApplicationId;
    }

    public void setTestApplicationId(String testApplicationId) {
        this.testApplicationId = testApplicationId;
    }

    @Input
    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @Input
    public String getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(String targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @Input
    public String getTestedApplicationId() {
        return testedApplicationId;
    }

    public void setTestedApplicationId(String testedApplicationId) {
        this.testedApplicationId = testedApplicationId;
    }

    @Input
    public String getInstrumentationRunner() {
        return instrumentationRunner;
    }

    public void setInstrumentationRunner(String instrumentationRunner) {
        this.instrumentationRunner = instrumentationRunner;
    }

    @Input
    public Boolean getHandleProfiling() {
        return handleProfiling;
    }

    public void setHandleProfiling(Boolean handleProfiling) {
        this.handleProfiling = handleProfiling;
    }

    @Input
    public Boolean getFunctionalTest() {
        return functionalTest;
    }

    public void setFunctionalTest(Boolean functionalTest) {
        this.functionalTest = functionalTest;
    }

    @Input
    public Map<String, Object> getPlaceholdersValues() {
        return placeholdersValues;
    }

    public void setPlaceholdersValues(
            Map<String, Object> placeholdersValues) {
        this.placeholdersValues = placeholdersValues;
    }

    public List<ManifestDependencyImpl> getLibraries() {
        return libraries;
    }

    public void setLibraries(
            List<ManifestDependencyImpl> libraries) {
        this.libraries = libraries;
    }

    /**
     * A synthetic input to allow gradle up-to-date checks to work.
     *
     * Since List<ManifestDependencyImpl> can't be used directly, as @Nested doesn't work on lists,
     * this method gathers and returns the underlying manifest files.
     */
    @SuppressWarnings("unused")
    @InputFiles
    public List<File> getLibraryManifests() {
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

    public static class ConfigAction implements TaskConfigAction<ProcessTestManifest> {

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
        public Class<ProcessTestManifest> getType() {
            return ProcessTestManifest.class;
        }

        @Override
        public void execute(final ProcessTestManifest processTestManifestTask) {

            final VariantConfiguration<CoreBuildType, CoreProductFlavor, CoreProductFlavor> config =
                    scope.getVariantConfiguration();

            processTestManifestTask.setTestManifestFile(config.getMainManifest());

            processTestManifestTask.setTmpDir(
                    new File(scope.getGlobalScope().getIntermediatesDir(), "manifest/tmp"));

            // get single output for now.
            final BaseVariantOutputData variantOutputData =
                    scope.getVariantData().getOutputs().get(0);

            variantOutputData.manifestProcessorTask = processTestManifestTask;

            processTestManifestTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processTestManifestTask.setVariantName(config.getFullName());

            processTestManifestTask.setTestApplicationId(config.getApplicationId());
            ConventionMappingHelper.map(processTestManifestTask, "minSdkVersion",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                                return scope.getGlobalScope().getAndroidBuilder()
                                        .getTargetCodename();
                            }
                            return config.getMinSdkVersion().getApiString();
                        }
                    });

            ConventionMappingHelper.map(processTestManifestTask, "targetSdkVersion",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                                return scope.getGlobalScope().getAndroidBuilder()
                                        .getTargetCodename();
                            }

                            return config.getTargetSdkVersion().getApiString();
                        }
                    });
            ConventionMappingHelper.map(processTestManifestTask, "testedApplicationId",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return config.getTestedApplicationId();
                        }
                    });
            ConventionMappingHelper.map(processTestManifestTask, "instrumentationRunner",
                    new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return config.getInstrumentationRunner();
                        }
                    });

            ConventionMappingHelper.map(processTestManifestTask, "handleProfiling",
                    new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return config.getHandleProfiling();
                        }
                    });
            ConventionMappingHelper.map(processTestManifestTask, "functionalTest",
                    new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return config.getFunctionalTest();
                        }
                    });

            ConventionMappingHelper.map(processTestManifestTask, "libraries",
                    new Callable<List<ManifestDependencyImpl>>() {
                        @Override
                        public List<ManifestDependencyImpl> call() throws Exception {
                            return DependencyManager.getManifestDependencies(
                                    config.getDirectLibraries());
                        }
                    });

            processTestManifestTask.setManifestOutputFile(
                    variantOutputData.getScope().getManifestOutputFile());

            ConventionMappingHelper.map(processTestManifestTask, "placeholdersValues",
                    new Callable<Map<String, Object>>() {
                        @Override
                        public Map<String, Object> call() throws Exception {
                            return config.getManifestPlaceholders();
                        }
                    });
        }
    }
}
