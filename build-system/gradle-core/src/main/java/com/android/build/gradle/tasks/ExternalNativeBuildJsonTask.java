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
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.ide.common.process.ProcessException;

import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * Task wrapper around ExternalNativeJsonGenerator.
 */
public class ExternalNativeBuildJsonTask extends BaseTask {

    private ExternalNativeJsonGenerator generator;

    @TaskAction
    public void build() throws ProcessException, IOException {
        generator.build();
    }

    @SuppressWarnings("unused")
    @Nested
    public ExternalNativeJsonGenerator getExternalNativeJsonGenerator() {
        return generator;
    }

    @NonNull
    public static TaskConfigAction<ExternalNativeBuildJsonTask>
        createTaskConfigAction(
            @NonNull final ExternalNativeJsonGenerator generator,
            @NonNull final VariantScope scope) {
        return new ConfigAction(scope, generator);
    }

    private static class ConfigAction implements TaskConfigAction<ExternalNativeBuildJsonTask> {

        private final VariantScope scope;
        private final ExternalNativeJsonGenerator generator;

        private ConfigAction(VariantScope scope, ExternalNativeJsonGenerator generator) {
            this.scope = scope;
            this.generator = generator;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("generateJsonModel");
        }

        @NonNull
        @Override
        public Class<ExternalNativeBuildJsonTask> getType() {
            return ExternalNativeBuildJsonTask.class;
        }

        @Override
        public void execute(@NonNull ExternalNativeBuildJsonTask task) {
            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.generator = generator;
        }
    }
}
