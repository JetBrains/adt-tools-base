/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.externalBuild;

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.PackagingScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PrePackageApplication;
import com.android.builder.core.DefaultDexOptions;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.util.EnumSet;

/**
 * Task Manager for External Build system integration.
 */
class ExternalBuildTaskManager {

    private final Project project;
    private final AndroidTaskRegistry androidTasks = new AndroidTaskRegistry();
    private final TaskContainerAdaptor tasks;

    ExternalBuildTaskManager(@NonNull Project project) {
        this.project = project;
        this.tasks = new TaskContainerAdaptor(project.getTasks());
    }

    void createTasks(@NonNull ExternalBuildExtension externalBuildExtension) throws Exception {

        // anchor task
        AndroidTask<ExternalBuildAnchorTask> externalBuildAnchorTask =
                androidTasks.create(tasks, new ExternalBuildAnchorTask.ConfigAction());

        ExternalBuildContext externalBuildContext = new ExternalBuildContext(
                externalBuildExtension);

        File file = project.file(externalBuildExtension.buildManifestPath);
        ExternalBuildManifestLoader.loadAndPopulateContext(
                file, project, externalBuildContext);

        ExtraModelInfo modelInfo = new ExtraModelInfo(project, false /* isLibrary */);
        TransformManager transformManager = new TransformManager(androidTasks, modelInfo);

        transformManager.addStream(OriginalStream.builder()
                .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                .addScope(QualifiedContent.Scope.PROJECT)
                .setJars(externalBuildContext::getInputJarFiles)
                .build());

        // add an empty java resources directory for now.
        transformManager.addStream(OriginalStream.builder()
                .addContentType(QualifiedContent.DefaultContentType.RESOURCES)
                .addScope(QualifiedContent.Scope.PROJECT)
                .setFolder(Files.createTempDir())
                .build());

        // add an empty native libraries resources directory for now.
        transformManager.addStream(OriginalStream.builder()
                .addContentType(ExtendedContentType.NATIVE_LIBS)
                .addScope(QualifiedContent.Scope.PROJECT)
                .setFolder(Files.createTempDir())
                .build());

        ExternalBuildGlobalScope globalScope = new ExternalBuildGlobalScope(project);
        ExternalBuildVariantScope variantScope = new ExternalBuildVariantScope(globalScope,
                project.getBuildDir(),
                externalBuildContext);

        // massage the manifest file.

        // Extract the passed jars into folders as the InstantRun transforms can only handle folders.
        ExtractJarsTransform extractJarsTransform = new ExtractJarsTransform(
                ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                ImmutableSet.of(QualifiedContent.Scope.PROJECT));
        AndroidTask<TransformTask> extractJarsTask = transformManager
                .addTransform(tasks, variantScope, extractJarsTransform);
        assert extractJarsTask != null;

        InstantRunTaskManager instantRunTaskManager = new InstantRunTaskManager(project.getLogger(),
                variantScope, transformManager, androidTasks, tasks);

        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        new DexOptions(modelInfo),
                        () -> null,
                        extractJarsTask,
                        externalBuildAnchorTask,
                        EnumSet.of(QualifiedContent.Scope.PROJECT),
                        () -> project.file(
                                externalBuildContext
                                        .getBuildManifest()
                                        .getAndroidManifest()
                                        .getExecRootPath()),
                        false /* addResourceVerifier */);

        extractJarsTask.dependsOn(tasks, buildInfoLoaderTask);

        AndroidTask<InstantRunWrapperTask> incrementalBuildWrapperTask =
                instantRunTaskManager.createInstantRunIncrementalTasks(project);

        externalBuildAnchorTask.dependsOn(tasks, incrementalBuildWrapperTask);

        // TODO: support multi-dex.
        DexTransform dexTransform =
                new DexTransform(
                        new DefaultDexOptions(),
                        true,
                        false,
                        null,
                        variantScope.getPreDexOutputDir(),
                        externalBuildContext.getAndroidBuilder(),
                        project.getLogger(),
                        variantScope.getInstantRunBuildContext());

        AndroidTask<TransformTask> dexTask =
                transformManager.addTransform(tasks, variantScope, dexTransform);

        // if we are in instant-run mode and the patching policy is relying on multi-dex shards,
        // we should run the dexing as part of the incremental build.
        if (InstantRunPatchingPolicy.PRE_LOLLIPOP
                != variantScope.getInstantRunBuildContext().getPatchingPolicy()) {
            incrementalBuildWrapperTask.dependsOn(tasks, dexTask);
        }

        AndroidTask<PrePackageApplication> prePackageApp =
                androidTasks.create(
                        tasks,
                        new PrePackageApplication.ConfigAction(
                                "prePackageMarkerFor", variantScope, variantScope));

        // TODO: what's the equivalent?
        // prePackageApp.dependsOn(tasks, variantScope.getInstantRunAnchorTask());

        PackagingScope packagingScope =
                new ExternalBuildPackagingScope(
                        project, externalBuildContext, variantScope, transformManager);

        // TODO: Where should assets come from?
        AndroidTask<Task> createAssetsDirectory =
                androidTasks.create(
                        tasks,
                        new TaskConfigAction<Task>() {
                            @NonNull
                            @Override
                            public String getName() {
                                return "createAssetsDirectory";
                            }

                            @NonNull
                            @Override
                            public Class<Task> getType() {
                                return Task.class;
                            }

                            @Override
                            public void execute(@NonNull Task task) {
                                task.doLast(t -> {
                                    FileUtils.mkdirs(variantScope.getAssetsDir());
                                });
                            }
                        });

        AndroidTask<PackageApplication> packageApp =
                androidTasks.create(
                        tasks,
                        new PackageApplication.ConfigAction(
                                packagingScope,
                                variantScope.getInstantRunBuildContext().getPatchingPolicy()));

        packageApp.dependsOn(tasks, createAssetsDirectory);

        for (TransformStream stream : transformManager.getStreams(StreamFilter.DEX)) {
            packageApp.dependsOn(tasks, stream.getDependencies());
        }

        for (TransformStream stream : transformManager.getStreams(StreamFilter.RESOURCES)) {
            packageApp.dependsOn(tasks, stream.getDependencies());
        }
        for (TransformStream stream : transformManager.getStreams(StreamFilter.NATIVE_LIBS)) {
            packageApp.dependsOn(tasks, stream.getDependencies());
        }

        externalBuildAnchorTask.dependsOn(tasks, packageApp);

        // TODO: build info?
    }
}
