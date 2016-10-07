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
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.InstantRunVariantScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;

import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * Task to disable execution of the InstantRun slicer, dexer and packager when they are not needed.
 *
 * <p>The next time they run they will pick up all intermediate changes.
 *
 * <p>With multi apk (N or above device) resources are packaged in the main split APK. However when a warm swap is possible, it is
 * not necessary to produce immediately the new main SPLIT since the runtime use directly the
 * resources.ap_ file. However, as soon as an incompatible change forcing a cold swap is
 * triggered, the main APK must be rebuilt (even if the resources were changed in a previous
 * build).
 */
public class PreColdSwapTask extends DefaultAndroidTask {

    private static final Logger LOG = Logging.getLogger(PreColdSwapTask.class);

    private TransformVariantScope transformVariantScope;
    private InstantRunVariantScope instantRunVariantScope;
    private InstantRunBuildContext instantRunContext;

    @TaskAction
    public void disableBuildTasksAsNeeded() throws IOException {
        LOG.info("PreColdSwapTask : build mode is %1$s",
                instantRunContext.getBuildMode().toString());

        switch (instantRunContext.getBuildMode()) {
            case HOT_WARM:
                // We can hot swap, don't produce the full apk.
                instantRunVariantScope.getColdSwapBuildTasks().forEach(this::disableTask);
                disableTask(instantRunVariantScope.getPackageApplicationTask());
                break;
            case COLD:
                if (instantRunContext.getPatchingPolicy() == InstantRunPatchingPolicy.MULTI_DEX) {
                    disableTask(instantRunVariantScope.getPackageApplicationTask());
                }
                break;
            case FULL:
                // Leave everything enabled.
                break;
            default:
                throw new AssertionError("Unknown " + InstantRunBuildMode.class.getName());
        }
    }

    private <T extends Task> void disableTask(AndroidTask<T> task) {
        LOG.info("Disabling task %1$s", task.getName());
        transformVariantScope.getGlobalScope().getProject().getTasks().getByName(task.getName())
                .setEnabled(false);
    }

    public static class ConfigAction implements TaskConfigAction<PreColdSwapTask> {

        @NonNull
        protected final TransformVariantScope transformVariantScope;
        @NonNull
        protected final InstantRunVariantScope instantRunVariantScope;
        @NonNull
        protected final String name;

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
        public Class<PreColdSwapTask> getType() {
            return PreColdSwapTask.class;
        }

        @Override
        public void execute(@NonNull PreColdSwapTask task) {
            task.setVariantName(instantRunVariantScope.getFullVariantName());
            task.transformVariantScope = transformVariantScope;
            task.instantRunVariantScope = instantRunVariantScope;
            task.instantRunContext = instantRunVariantScope.getInstantRunBuildContext();
        }
    }
}
