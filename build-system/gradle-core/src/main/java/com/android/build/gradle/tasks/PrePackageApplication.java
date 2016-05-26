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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.builder.model.OptionalCompilationStep;

import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * PrePackaging step class that will look if the packaging of the main APK split is necessary
 * when running in InstantRun mode. In InstantRun mode targeting an api 23 or above device,
 * resources are packaged in the main split APK. However when a warm swap is possible, it is
 * not necessary to produce immediately the new main SPLIT since the runtime use directly the
 * resources.ap_ file. However, as soon as an incompatible change forcing a cold swap is
 * triggered, the main APK must be rebuilt (even if the resources were changed in a previous
 * build).
 */
public class PrePackageApplication extends DefaultAndroidTask {

    InstantRunBuildContext instantRunContext;
    InstantRunVariantScope variantScope;

    @TaskAction
    public void doFullTaskAction() throws IOException {

        // when instantRun is disabled or not targeting 23 and above, we must run the packageApp
        // task.
        if (InstantRunPatchingPolicy.MULTI_APK != instantRunContext.getPatchingPolicy()
                || variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY)) {
            return;
        }

        if (instantRunContext.hasPassedVerification()) {
            disablePackageTask();
        } else {
            // now the main apk is only necessary if we produced a RESOURCES file in a previous
            // build, let's check it out.
            if (instantRunContext.getPastBuildsArtifactForType(
                    InstantRunBuildContext.FileType.RESOURCES) == null) {
                disablePackageTask();
            }
        }
    }

    private void disablePackageTask() {
        variantScope.getGlobalScope().getProject().getTasks().getByName(
                variantScope.getPackageApplicationTask().getName()).setEnabled(false);
    }

    public static class ConfigAction implements TaskConfigAction<PrePackageApplication> {

        @NonNull
        protected final InstantRunVariantScope instantRunVariantScope;
        @NonNull
        protected final TransformVariantScope transformVariantScope;
        @NonNull
        protected final String name;

        public ConfigAction(@NonNull String name, @NonNull VariantScope variantScope) {
            this(name, variantScope, variantScope);
        }

        public ConfigAction(@NonNull String name,
                @NonNull TransformVariantScope transformVariantScope,
                @NonNull InstantRunVariantScope instantRunVariantScope) {
            this.name = name;
            this.transformVariantScope = transformVariantScope;
            this.instantRunVariantScope = instantRunVariantScope;
        }

        @Override
        @NonNull
        public String getName() {
            return transformVariantScope.getTaskName(name);
        }

        @NonNull
        @Override
        public Class<PrePackageApplication> getType() {
            return PrePackageApplication.class;
        }

        @Override
        public void execute(@NonNull PrePackageApplication task) {
            task.setVariantName(transformVariantScope.getFullVariantName());
            task.instantRunContext = instantRunVariantScope.getInstantRunBuildContext();
            task.variantScope = instantRunVariantScope;
        }
    }
}
