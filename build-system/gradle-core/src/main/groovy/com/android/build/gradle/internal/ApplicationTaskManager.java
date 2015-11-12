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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.tasks.fd.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.fd.GenerateInstantRunAppInfoTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * TaskManager for creating tasks in an Android application project.
 */
public class ApplicationTaskManager extends TaskManager {

    public ApplicationTaskManager(
            Project project,
            AndroidBuilder androidBuilder,
            DataBindingBuilder dataBindingBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, dataBindingBuilder, extension, sdkHandler,dependencyManager,
                toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        assert variantData instanceof ApplicationVariantData;
        final ApplicationVariantData appVariantData = (ApplicationVariantData) variantData;

        final VariantScope variantScope = variantData.getScope();

        createAnchorTasks(tasks, variantScope);
        createCheckManifestTask(tasks, variantScope);

        handleMicroApp(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        // Add a task to process the manifest(s)
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeAppManifestsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to create the res values
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createGenerateResValuesTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to compile renderscript files.
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createRenderscriptTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to merge the resource folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeResourcesTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to merge the asset folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeAssetsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to create the BuildConfig class
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createBuildConfigTask(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        // Add a task to process the Android Resources and generate source files
                        createApkProcessResTask(tasks, variantScope);

                        // Add a task to process the java resources
                        createProcessJavaResTasks(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_AIDL_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createAidlTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a compile task
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_COMPILE_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        AndroidTask<? extends JavaCompile> javacTask =
                                createJavacTask(tasks, variantScope);

                        if (variantData.getVariantConfiguration().getUseJack()) {
                            createJackTask(tasks, variantScope);
                        } else {
                            setJavaCompilerTask(javacTask, tasks, variantScope);
                            createJarTasks(tasks, variantScope);
                            createPostCompilationTasks(tasks, variantScope);
                        }
                        return null;
                    }
                });

        // Add data binding tasks if enabled
        if (extension.getDataBinding().isEnabled()) {
            createDataBindingTasks(tasks, variantScope);
        }

        // Add NDK tasks
        if (isNdkTaskNeeded) {
            ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_NDK_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() {
                            createNdkTasks(variantScope);
                            return null;
                        }
                    });
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // Add a task to merge the jni libs folders
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_MERGE_JNILIBS_FOLDERS_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeJniLibFoldersTasks(tasks, variantScope);
                        return null;
                    }
                });

        if (variantData.getSplitHandlingPolicy().equals(
                BaseVariantData.SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY)) {
            if (getExtension().getBuildToolsRevision().getMajor() < 21) {
                throw new RuntimeException("Pure splits can only be used with buildtools 21 and later");
            }

            ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_SPLIT_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() {
                            createSplitTasks(tasks, variantScope);
                            return null;
                        }
                    });
        }

        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_PACKAGING_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createPackagingTask(tasks, variantScope, true /*publishApk*/);
                        return null;
                    }
                });

        // create the lint tasks.
        ThreadRecorder.get().record(ExecutionType.APP_TASK_MANAGER_CREATE_LINT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createLintTasks(tasks, variantScope);
                        return null;
                    }
                });
    }


    /**
     * Create InstantRun related tasks that should be ran right after the java compilation task.
     */
    @Override
    protected void createIncrementalSupportTasks(TaskFactory tasks, VariantScope variantScope) {

        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            TransformManager transformManager = variantScope.getTransformManager();

            ExtractJarsTransform extractJarsTransform = new ExtractJarsTransform(
                    ImmutableSet.<QualifiedContent.ContentType>of(
                            QualifiedContent.DefaultContentType.CLASSES),
                    ImmutableSet.of(Scope.SUB_PROJECTS));
            AndroidTask<TransformTask> extractJarsTask = transformManager
                    .addTransform(tasks, variantScope, extractJarsTransform);

            // always run the verifier first, since if it detects incompatible changes, we
            // should skip bytecode enhancements of the changed classes.
            InstantRunVerifierTransform verifierTransform =
                    new InstantRunVerifierTransform(variantScope);
            AndroidTask<TransformTask> verifierTask = transformManager
                    .addTransform(tasks, variantScope, verifierTransform);
            verifierTask.dependsOn(tasks, extractJarsTask);

            InstantRunTransform instantRunTransform = new InstantRunTransform(variantScope);
            AndroidTask<TransformTask> instantRunTask = transformManager
                    .addTransform(tasks, variantScope, instantRunTransform);
            instantRunTask.dependsOn(tasks, verifierTask);

            AndroidTask<FastDeployRuntimeExtractorTask> extractorTask = getAndroidTasks().create(
                    tasks, new FastDeployRuntimeExtractorTask.ConfigAction(variantScope));

            // also add a new stream for the extractor task output.
            variantScope.getTransformManager().addStream(OriginalStream.builder()
                    .addContentTypes(TransformManager.CONTENT_CLASS)
                    .addScope(Scope.EXTERNAL_LIBRARIES)
                    .setJar(variantScope.getIncrementalRuntimeSupportJar())
                    .setDependency(extractorTask.get(tasks))
                    .build());

            AndroidTask<GenerateInstantRunAppInfoTask> generateAppInfoAndroidTask
                    = getAndroidTasks()
                    .create(tasks, new GenerateInstantRunAppInfoTask.ConfigAction(
                            variantScope));

            // create the AppInfo.class for this variant.
            GenerateInstantRunAppInfoTask generateInstantRunAppInfoTask
                    = generateAppInfoAndroidTask.get(tasks);

            // make the task that generates the AppInfo dependent on the first merge manifest task
            // so we can get its output file.
            BaseVariantOutputData outputData = Iterators
                    .get(variantScope.getVariantData().getOutputs().iterator(), 0);
            generateAppInfoAndroidTask.dependsOn(tasks, outputData.manifestProcessorTask);

            // also add a new stream for the injector task output.
            variantScope.getTransformManager().addStream(OriginalStream.builder()
                    .addContentTypes(TransformManager.CONTENT_CLASS)
                    .addScope(Scope.EXTERNAL_LIBRARIES)
                    .setJar(generateInstantRunAppInfoTask.getOutputFile())
                    .setDependency(generateInstantRunAppInfoTask)
                    .build());
        }
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * Configure variantData to generate embedded wear application.
     */
    private void handleMicroApp(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
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
