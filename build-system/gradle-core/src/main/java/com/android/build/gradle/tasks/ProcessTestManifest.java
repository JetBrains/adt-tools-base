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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.AndroidLibrary;
import com.android.io.FileWrapper;
import com.android.xml.AndroidManifest;
import com.google.common.collect.Lists;

import org.gradle.api.artifacts.Configuration;
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

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 * <p>For both test modules and tests in androidTest process is the same, expect
 * for how the tested application id is extracted.</p>
 *
 * <p>Tests in androidTest get that info form the
 * {@link VariantConfiguration#getTestedApplicationId()}, while the test modules get the info from
 * the {@link com.android.build.gradle.internal.publishing.ManifestPublishArtifact} of the
 * tested app.</p>
 */
@ParallelizableTask
public class ProcessTestManifest extends ManifestProcessorTask {

    @Nullable
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
    private List<AndroidLibrary> libraries;

    @Nullable
    private String testLabel;

    @Override
    protected void doFullTaskAction() throws IOException {
        getBuilder().mergeManifestsForTestVariant(
                getTestApplicationId(),
                getMinSdkVersion(),
                getTargetSdkVersion(),
                getTestedApplicationId(),
                getInstrumentationRunner(),
                getHandleProfiling(),
                getFunctionalTest(),
                getTestLabel(),
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
    @Optional
    public String getTestLabel() {
        return testLabel;
    }

    public void setTestLabel(String testLabel) {
        this.testLabel = testLabel;
    }

    @Input
    public Map<String, Object> getPlaceholdersValues() {
        return placeholdersValues;
    }

    public void setPlaceholdersValues(
            Map<String, Object> placeholdersValues) {
        this.placeholdersValues = placeholdersValues;
    }

    public List<AndroidLibrary> getLibraries() {
        return libraries;
    }

    public void setLibraries(
            List<AndroidLibrary> libraries) {
        this.libraries = libraries;
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

    public static class ConfigAction implements TaskConfigAction<ProcessTestManifest> {

        @NonNull
        private final VariantScope scope;

        @Nullable
        private final Configuration targetManifestConfiguration;

        public ConfigAction(@NonNull VariantScope scope) {
            this(scope, null);
        }

        public ConfigAction(
                @NonNull VariantScope scope, @Nullable Configuration targetManifestConfiguration){
            this.scope = scope;
            this.targetManifestConfiguration = targetManifestConfiguration;
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
        public void execute(@NonNull final ProcessTestManifest processTestManifestTask) {

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
            processTestManifestTask.setTestApplicationId(config.getTestApplicationId());

            ConventionMappingHelper.map(processTestManifestTask, "minSdkVersion", () -> {
                        if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                            return scope.getGlobalScope().getAndroidBuilder()
                                    .getTargetCodename();
                        }
                        return config.getMinSdkVersion().getApiString();
                    });

            ConventionMappingHelper.map(processTestManifestTask, "targetSdkVersion", () -> {
                        if (scope.getGlobalScope().getAndroidBuilder().isPreviewTarget()) {
                            return scope.getGlobalScope().getAndroidBuilder()
                                    .getTargetCodename();
                        }

                        return config.getTargetSdkVersion().getApiString();
                    });

            if (targetManifestConfiguration != null){
                // it is a task for the test module, get the tested application id from its manifest
                ConventionMappingHelper.map(processTestManifestTask, "testedApplicationId", () ->
                        AndroidManifest.getPackage(
                                new FileWrapper(
                                        targetManifestConfiguration
                                                .getSingleFile().getAbsolutePath())));
            }
            else {
                ConventionMappingHelper.map(
                        processTestManifestTask, "testedApplicationId", () ->{
                            String testedApp = config.getTestedApplicationId();
                            // we should not be null, although the above method is @Nullable
                            assert testedApp != null;
                            return testedApp;
                        });
            }

            ConventionMappingHelper.map(
                    processTestManifestTask, "instrumentationRunner",
                    config::getInstrumentationRunner);
            ConventionMappingHelper.map(
                    processTestManifestTask, "handleProfiling", config::getHandleProfiling);
            ConventionMappingHelper.map(
                    processTestManifestTask, "functionalTest", config::getFunctionalTest);
            ConventionMappingHelper.map(
                    processTestManifestTask, "testLabel", config::getTestLabel);
            ConventionMappingHelper.map(
                    processTestManifestTask, "libraries", config::getCompileAndroidLibraries);

            processTestManifestTask.setManifestOutputFile(
                    variantOutputData.getScope().getManifestOutputFile());

            ConventionMappingHelper.map(
                    processTestManifestTask, "placeholdersValues", config::getManifestPlaceholders);
        }
    }
}
