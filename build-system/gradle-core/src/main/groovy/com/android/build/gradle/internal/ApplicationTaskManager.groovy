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

package com.android.build.gradle.internal

import com.android.annotations.NonNull
import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.profile.SpanRecorders
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.AndroidBuilder
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

/**
 * TaskManager for creating tasks in an Android application project.
 */
class ApplicationTaskManager extends TaskManager {
    public ApplicationTaskManager (
            Project project,
            AndroidBuilder androidBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, extension, sdkHandler, dependencyManager, toolingRegistry)
    }

    @Override
    public void  createTasksForVariantData(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof ApplicationVariantData;
        ApplicationVariantData appVariantData = (ApplicationVariantData) variantData;

        VariantScope variantScope = variantData.getScope()

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        handleMicroApp(tasks, variantScope);

        // Add a task to process the manifest(s)
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK) {
            createMergeAppManifestsTask(tasks, variantScope)
        }

        // Add a task to create the res values
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK) {
            createGenerateResValuesTask(tasks, variantScope);
        }

        // Add a task to compile renderscript files.
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK) {
            createRenderscriptTask(tasks, variantScope);
        }

        // Add a task to merge the resource folders
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK) {
            createMergeResourcesTask(tasks, variantScope);
        }

        // Add a task to merge the asset folders
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK) {
            createMergeAssetsTask(tasks, variantScope);
        }

        // Add a task to create the BuildConfig class
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK) {
            createBuildConfigTask(tasks, variantScope);
        }

        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_PREPROCESS_RESOURCES_TASK) {
            createPreprocessResourcesTask(tasks, variantScope)
        }

        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK) {
            // Add a task to process the Android Resources and generate source files
            createProcessResTask(tasks, variantScope, true /*generateResourcePackage*/);

            // Add a task to process the java resources
            createProcessJavaResTask(tasks, variantScope);
        }

        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK) {
            createAidlTask(tasks, variantScope);
        }

        // Add a compile task
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK) {
            if (variantData.getVariantConfiguration().getUseJack()) {
                createJackTask(appVariantData, null /*testedVariant*/);
            } else {
                createJavaCompileTask(tasks, variantScope);
                createJarTask(tasks, variantScope);
                createPostCompilationTasks(tasks, variantScope);
            }
        }

        // Add NDK tasks
        if (isNdkTaskNeeded) {
            SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK) {
                createNdkTasks(variantData);
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData))
        variantScope.setNdkOutputDirectories(getNdkOutputDirectories(variantData))

        if (variantData.getSplitHandlingPolicy() ==
                BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
            if (extension.getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException("Pure splits can only be used with buildtools 21 and later")
            }
            SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK) {
                createSplitResourcesTasks(variantScope);
                createSplitAbiTasks(variantScope);
            }
        }
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK) {
            createPackagingTask(tasks, variantScope, true /*publishApk*/);
        }

        // create the lint tasks.
        SpanRecorders.record(ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK) {
            createLintTasks(tasks, variantScope);
        }
    }

    /**
     * Configure variantData to generate embedded wear application.
     */
    private void handleMicroApp(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.variantData
        if (variantData.getVariantConfiguration().getBuildType().isEmbedMicroApp()) {
            // get all possible configurations for the variant. We'll take the highest priority
            // of them that have a file.
            List<String> wearConfigNames = variantData.getWearConfigNames();

            for (String configName : wearConfigNames) {
                Configuration config = project.getConfigurations().findByName(
                        configName);
                // this shouldn't happen, but better safe.
                if (config == null) {
                    continue;
                }

                Set<File> file = config.getFiles();

                int count = file.size();
                if (count == 1) {
                    createGenerateMicroApkDataTask(tasks, scope, config);
                    // found one, bail out.
                    return;
                } else if (count > 1) {
                    throw new RuntimeException(String.format(
                            "Configuration '%s' resolves to more than one apk.", configName));
                }
            }
        }
    }
}
