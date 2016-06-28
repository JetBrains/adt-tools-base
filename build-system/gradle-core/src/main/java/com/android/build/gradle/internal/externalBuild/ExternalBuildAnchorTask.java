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
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.BaseTask;

/**
 * Anchor task for all tasks related to an external build system integration with Instant Run.
 */
public class ExternalBuildAnchorTask extends BaseTask {

    public static class ConfigAction implements TaskConfigAction<ExternalBuildAnchorTask> {

        @NonNull
        @Override
        public String getName() {
            return "process";
        }

        @NonNull
        @Override
        public Class<ExternalBuildAnchorTask> getType() {
            return ExternalBuildAnchorTask.class;
        }

        @Override
        public void execute(@NonNull ExternalBuildAnchorTask task) {
            task.setVariantName("debug");
        }
    }
}