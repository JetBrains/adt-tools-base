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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.InstantRunAnchorTask;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.transforms.InstantRunBuildType;
import com.android.build.gradle.internal.transforms.InstantRunDex;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.build.gradle.internal.transforms.InstantRunVerifierTransform;
import com.android.build.gradle.internal.transforms.NoChangesVerifierTransform;
import com.android.build.gradle.tasks.ColdswapArtifactsKickerTask;
import com.android.build.gradle.tasks.fd.FastDeployRuntimeExtractorTask;
import com.android.build.gradle.tasks.fd.GenerateInstantRunAppInfoTask;
import com.android.builder.core.DexByteCodeConverter;
import com.android.builder.core.DexOptions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Task Manager for InstantRun related transforms configuration and tasks handling.
 */
public class InstantRunTaskManager {

    private AndroidTask<TransformTask> verifierTask;

    @NonNull
    private final Logger logger;

    @NonNull
    private final InstantRunVariantScope variantScope;

    @NonNull
    private final TransformManager transformManager;

    @NonNull
    private final AndroidTaskRegistry androidTasks;

    @NonNull
    private final TaskFactory tasks;

    public InstantRunTaskManager(@NonNull Logger logger,
            @NonNull InstantRunVariantScope instantRunVariantScope,
            @NonNull TransformManager transformManager,
            @NonNull AndroidTaskRegistry androidTasks,
            @NonNull TaskFactory tasks) {
        this.logger = logger;
        this.variantScope = instantRunVariantScope;
        this.transformManager = transformManager;
        this.androidTasks = androidTasks;
        this.tasks = tasks;
    }


    public AndroidTask<BuildInfoLoaderTask> createInstantRunAllTasks(
            DexOptions dexOptions,
            @NonNull Supplier<DexByteCodeConverter> dexByteCodeConverter,
            AndroidTask<?> preTask,
            AndroidTask<?> anchorTask,
            Set<QualifiedContent.Scope> resMergingScopes,
            Supplier<File> instantRunMergedManifest,
            boolean addResourceVerifier) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();

        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask = androidTasks.create(tasks,
                new BuildInfoLoaderTask.ConfigAction(variantScope, logger));

        // always run the verifier first, since if it detects incompatible changes, we
        // should skip bytecode enhancements of the changed classes.
        InstantRunVerifierTransform verifierTransform = new InstantRunVerifierTransform(variantScope);
        verifierTask = transformManager.addTransform(
                tasks, transformVariantScope, verifierTransform);
        assert verifierTask!= null;
        verifierTask.dependsOn(tasks, preTask);

        NoChangesVerifierTransform jniLibsVerifierTransform = new NoChangesVerifierTransform(
                variantScope,
                ImmutableSet.of(QualifiedContent.DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS),
                resMergingScopes, InstantRunVerifierStatus.JAVA_RESOURCES_CHANGED,
                true /* abortBuild */);
        AndroidTask<TransformTask> jniLibsVerifierTask =
                transformManager.addTransform(
                        tasks,
                        transformVariantScope,
                        jniLibsVerifierTransform);
        assert jniLibsVerifierTask!=null;
        jniLibsVerifierTask.dependsOn(tasks, verifierTask);

        InstantRunTransform instantRunTransform = new InstantRunTransform(variantScope);
        AndroidTask<TransformTask> instantRunTask = transformManager
                .addTransform(tasks, transformVariantScope, instantRunTransform);
        assert instantRunTask!=null;

        if (addResourceVerifier) {
            NoChangesVerifierTransform dependenciesVerifierTransform =
                    new NoChangesVerifierTransform(
                            variantScope,
                            ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                            Sets.immutableEnumSet(
                                    QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                                    QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                                    QualifiedContent.Scope.EXTERNAL_LIBRARIES),
                            InstantRunVerifierStatus.DEPENDENCY_CHANGED,
                            false /* abortBuild */);
            AndroidTask<TransformTask> dependenciesVerifierTask =
                    transformManager.addTransform(
                            tasks,
                            transformVariantScope,
                            dependenciesVerifierTransform);
            assert dependenciesVerifierTask != null;
            dependenciesVerifierTask.dependsOn(tasks, verifierTask);
            instantRunTask.dependsOn(tasks, dependenciesVerifierTask);
        }

        instantRunTask.dependsOn(tasks, buildInfoLoaderTask, verifierTask, jniLibsVerifierTask);

        AndroidTask<FastDeployRuntimeExtractorTask> extractorTask = androidTasks.create(
                tasks, new FastDeployRuntimeExtractorTask.ConfigAction(variantScope));

        // also add a new stream for the extractor task output.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setJar(variantScope.getIncrementalRuntimeSupportJar())
                .setDependency(extractorTask.get(tasks))
                .build());

        AndroidTask<GenerateInstantRunAppInfoTask> generateAppInfoAndroidTask = androidTasks
                .create(tasks, new GenerateInstantRunAppInfoTask.ConfigAction(
                        transformVariantScope, variantScope, instantRunMergedManifest));

        // create the AppInfo.class for this variant.
        // TODO: remove this get() as it forces task creation.
        GenerateInstantRunAppInfoTask generateInstantRunAppInfoTask =
                generateAppInfoAndroidTask.get(tasks);

        // also add a new stream for the injector task output.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                .setJar(generateInstantRunAppInfoTask.getOutputFile())
                .setDependency(generateInstantRunAppInfoTask)
                .build());

        anchorTask.dependsOn(tasks, instantRunTask);

        // we always produce the reload.dex irrespective of the targeted version,
        // and if we are not in incremental mode, we need to still need to clean our output state.
        InstantRunDex reloadDexTransform = new InstantRunDex(
                variantScope,
                dexByteCodeConverter,
                dexOptions,
                logger);

        AndroidTask<TransformTask> reloadDexing = transformManager
                .addTransform(tasks, transformVariantScope, reloadDexTransform);

        anchorTask.dependsOn(tasks, reloadDexing);

        return buildInfoLoaderTask;
    }


    /**
     * Creates all InstantRun related transforms after compilation.
     */
    @NonNull
    public AndroidTask<InstantRunWrapperTask> createInstantRunIncrementalTasks(
            @NonNull Project project) {

        TransformVariantScope transformVariantScope = variantScope.getTransformVariantScope();

        // we are creating two anchor tasks
        // 1. allActionAnchorTask to anchor tasks that should be executed whether a full build or
        //    incremental build is invoked.
        // 2. incrementalAnchorTask to anchor tasks that should only be executed when an
        //    incremental build is requested.
        // the incrementalAnchorTask will therefore depend on the allActionAnchorTask.
        AndroidTask<InstantRunAnchorTask> incrementalAnchorTask = androidTasks.create(tasks,
                new InstantRunAnchorTask.ConfigAction(transformVariantScope));

        // create the incremental version of the build-info.xml, another task will take care
        // of generating the build-info.xml when a full build is invoked.
        AndroidTask<InstantRunWrapperTask> incrementalWrapperTask = androidTasks.create(tasks,
                new InstantRunWrapperTask.ConfigAction(
                        variantScope, InstantRunWrapperTask.TaskType.INCREMENTAL, logger));

        // this will force build-info.xml to be generated only when the external task is directly
        // invoked by the IDE.
        incrementalAnchorTask.dependsOn(tasks, incrementalWrapperTask);

        variantScope.getInstantRunBuildContext().setApiLevel(
                AndroidGradleOptions.getTargetApiLevel(project),
                AndroidGradleOptions.getColdswapMode(project),
                AndroidGradleOptions.getBuildTargetAbi(project));
        variantScope.getInstantRunBuildContext().setDensity(
                AndroidGradleOptions.getBuildTargetDensity(project));
        InstantRunPatchingPolicy patchingPolicy =
                variantScope.getInstantRunBuildContext().getPatchingPolicy();


        // let's create the coldswap kicker task. It is necessary as sometimes the IDE will
        // request an assembleDebug to get the latest coldswap bits yet without any user changes.
        // so we need to manually kick the tasks that accumulated changes during reload.dex
        // iterations so they produce the artifacts.
        AndroidTask<ColdswapArtifactsKickerTask> coldswapKickerTask = androidTasks.create(
                tasks, new ColdswapArtifactsKickerTask.ConfigAction("coldswapKicker",
                        transformVariantScope,
                        variantScope));

        // this kicker task is dependent on the verifier and associated tasks result.
        coldswapKickerTask.dependsOn(tasks, verifierTask);

        if (patchingPolicy == InstantRunPatchingPolicy.PRE_LOLLIPOP) {
            // for Dalvik, we cannot cold swap.
            incrementalWrapperTask.dependsOn(tasks, coldswapKickerTask);
        } else {
            // if we are at API 21 or above, we generate multi-dexes.
            // this transform and all its dependencies will also run in full build mode as
            // it is automatically enrolled by the transform manager.
            InstantRunSlicer slicer = new InstantRunSlicer(logger, variantScope);
            AndroidTask<TransformTask> slicing = transformManager
                    .addTransform(tasks, transformVariantScope, slicer);

            // slicing should only happen if we need to produce the restart dexes.
            assert slicing!=null;
            slicing.dependsOn(tasks, coldswapKickerTask);
            variantScope.setInstantRunSlicerTask(slicing);


            incrementalWrapperTask.dependsOn(tasks, slicing);
        }

        return incrementalWrapperTask;
    }

}
