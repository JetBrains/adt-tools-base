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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import org.gradle.api.tasks.TaskAction;


/**
 * Simple task used as an anchor task for all instant run related tasks. An anchor task can be used
 * to conveniently set dependencies.
 */
public class InstantRunAnchorTask extends BaseTask {

    @TaskAction
    public void executeAction() {
    }

    public static class ConfigAction implements TaskConfigAction<InstantRunAnchorTask> {

        /**
         * Task name for Instant Run incremental build external anchor task.
         */
        public static String getName(VariantScope scope) {
            return scope.getTaskName("incremental", "SupportDex");
        }

        private final String taskName;
        private final VariantScope variantScope;

        public ConfigAction(VariantScope scope) {
            this.taskName = ConfigAction.getName(scope);
            this.variantScope = scope;
        }

        /**
         * Creates a new anchor task with a dedicated prefix.
         */
        public ConfigAction(VariantScope scope, String prefix) {
            this.taskName = scope.getTaskName("incremental", prefix);
            this.variantScope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<InstantRunAnchorTask> getType() {
            return InstantRunAnchorTask.class;
        }

        @Override
        public void execute(@NonNull InstantRunAnchorTask task) {
            task.setDescription("InstantRun task to build incremental artifacts");
            task.setVariantName(variantScope.getVariantConfiguration().getFullName());
        }
    }
}
