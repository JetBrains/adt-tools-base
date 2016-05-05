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
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.transforms.InstantRunTransform;
import com.android.sdklib.AndroidVersion;
import com.google.devtools.build.lib.rules.android.apkmanifest.ExternalBuildApkManifest;

import org.gradle.api.Action;
import org.gradle.api.Project;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

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
                file, project.getProjectDir(), externalBuildContext);

        ExtraModelInfo modelInfo = new ExtraModelInfo(project, false /* isLibrary */);
        TransformManager transformManager = new TransformManager(androidTasks, modelInfo);

        transformManager.addStream(OriginalStream.builder()
                .addContentType(QualifiedContent.DefaultContentType.CLASSES)
                .addScope(QualifiedContent.Scope.PROJECT)
                .setJars(externalBuildContext::getInputJarFiles)
                .build());

        ExternalBuildGlobalScope globalScope = new ExternalBuildGlobalScope(project);
        ExternalBuildVariantScope variantScope = new ExternalBuildVariantScope(globalScope,
                project.getBuildDir(),
                externalBuildContext);

        variantScope.getInstantRunBuildContext().setApiLevel(
                AndroidVersion.ART_RUNTIME, ColdswapMode.AUTO.toString(), "arm");

        // register transform.
        InstantRunTransform instantRunTransform = new InstantRunTransform(variantScope);
        AndroidTask<TransformTask> instantRunTransformTask =
                transformManager.addTransform(tasks, variantScope, instantRunTransform);

        if (instantRunTransformTask == null) {
            throw new RuntimeException("Unable to set up InstantRun related transforms");
        }
        externalBuildAnchorTask.dependsOn(tasks, instantRunTransformTask);
    }
}
