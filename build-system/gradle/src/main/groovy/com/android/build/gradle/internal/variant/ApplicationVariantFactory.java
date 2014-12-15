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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.ApkVariantImpl;
import com.android.build.gradle.internal.api.ApkVariantOutputImpl;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.builder.core.VariantType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 */
public class ApplicationVariantFactory implements VariantFactory<ApplicationVariantData> {

    @NonNull
    private final BasePlugin basePlugin;
    @NonNull
    private final TaskManager taskManager;

    public ApplicationVariantFactory(
            @NonNull BasePlugin basePlugin,
            @NonNull TaskManager taskManager) {
        this.basePlugin = basePlugin;
        this.taskManager = taskManager;
    }

    @Override
    @NonNull
    public ApplicationVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull Set<String> densities,
            @NonNull Set<String> abis,
            @NonNull Set<String> compatibleScreens) {
        ApplicationVariantData variant = new ApplicationVariantData(basePlugin, variantConfiguration);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(compatibleScreens);
        }

        // create its outputs
        if (variant.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.PRE_21_POLICY) {
            // create its outputs
            for (String density : densities) {
                for (String abi : abis) {
                    ImmutableList.Builder<FilterData> builder = ImmutableList.builder();
                    if (density != null) {
                        builder.add(FilterDataImpl.Builder.build(OutputFile.DENSITY, density));
                    }
                    if (abi != null) {
                        builder.add(FilterDataImpl.Builder.build(OutputFile.ABI, abi));
                    }
                    variant.createOutput(
                            OutputFile.OutputType.FULL_SPLIT,
                            builder.build());
                }
            }
        } else {
            variant.createOutput(OutputFile.OutputType.MAIN,
                    Collections.<FilterData>emptyList());
        }

        return variant;
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        // create the base variant object.
        ApplicationVariantImpl variant = basePlugin.getInstantiator().newInstance(
                ApplicationVariantImpl.class, variantData, basePlugin, readOnlyObjectProvider);

        // now create the output objects
        createApkOutputApiObjects(basePlugin, variantData, variant);

        return variant;
    }

    public static void createApkOutputApiObjects(
            @NonNull BasePlugin basePlugin,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull ApkVariantImpl variant) {
        List<? extends BaseVariantOutputData> outputList = variantData.getOutputs();
        List<BaseVariantOutput> apiOutputList = Lists.newArrayListWithCapacity(outputList.size());

        for (BaseVariantOutputData variantOutputData : outputList) {
            ApkVariantOutputData apkOutput = (ApkVariantOutputData) variantOutputData;

            ApkVariantOutputImpl output = basePlugin.getInstantiator().newInstance(
                    ApkVariantOutputImpl.class, apkOutput);

            apiOutputList.add(output);
        }

        variant.addOutputs(apiOutputList);
    }

    @NonNull
    @Override
    public VariantType getVariantConfigurationType() {
        return VariantType.DEFAULT;
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    /**
     * Creates the tasks for a given ApplicationVariantData.
     * @param variantData the non-null ApplicationVariantData.
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     */
    @Override
    public void createTasks(
            @NonNull BaseVariantData<?> variantData,
            @Nullable Task assembleTask) {

        assert variantData instanceof ApplicationVariantData;
        ApplicationVariantData appVariantData = (ApplicationVariantData) variantData;

        taskManager.createAnchorTasks(variantData);
        taskManager.createCheckManifestTask(variantData);

        handleMicroApp(variantData);

        // Add a task to process the manifest(s)
        taskManager.createMergeAppManifestsTask(variantData);

        // Add a task to create the res values
        taskManager.createGenerateResValuesTask(variantData);

        // Add a task to compile renderscript files.
        taskManager.createRenderscriptTask(variantData);

        // Add a task to merge the resource folders
        taskManager.createMergeResourcesTask(variantData, true /*process9Patch*/);

        // Add a task to merge the asset folders
        taskManager.createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/);

        // Add a task to create the BuildConfig class
        taskManager.createBuildConfigTask(variantData);

        // Add a task to process the Android Resources and generate source files
        taskManager.createProcessResTask(variantData, true /*generateResourcePackage*/);

        // Add a task to process the java resources
        taskManager.createProcessJavaResTask(variantData);

        taskManager.createAidlTask(variantData, null /*parcelableDir*/);

        // Add a compile task
        if (variantData.getVariantConfiguration().getUseJack()) {
            taskManager.createJackTask(appVariantData, null /*testedVariant*/);
        } else{
            taskManager.createCompileTask(variantData, null /*testedVariant*/);

            taskManager.createPostCompilationTasks(appVariantData);
        }

        // Add NDK tasks
        if (!basePlugin.getExtension().getUseNewNativePlugin()) {
            taskManager.createNdkTasks(variantData);
        }

        if (variantData.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
            taskManager.createSplitResourcesTasks(appVariantData);
            taskManager.createSplitAbiTasks(appVariantData);
        }

        taskManager.createPackagingTask(appVariantData, assembleTask, true /*publishApk*/);
    }

    @Override
    public void validateModel(VariantModel model){
        // No additional checks for ApplicationVariantFactory, so just return.
    }

    private void handleMicroApp(@NonNull BaseVariantData<?> variantData) {
        if (variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
            // get all possible configurations for the variant. We'll take the highest priority
            // of them that have a file.
            List<String> wearConfigNames = variantData.getWearConfigNames();

            for (String configName : wearConfigNames) {
                Configuration config = basePlugin.getProject().getConfigurations().findByName(
                        configName);
                // this shouldn't happen, but better safe.
                if (config == null) {
                    continue;
                }

                Set<File> file = config.getFiles();

                int count = file.size();
                if (count == 1) {
                    taskManager.createGenerateMicroApkDataTask(variantData, config);
                    // found one, bail out.
                    return;
                } else if (count > 1) {
                    throw new RuntimeException(String.format(
                            "Configuration '%1$s' resolves to more than one apk.", configName));
                }
            }
        }
    }
}
