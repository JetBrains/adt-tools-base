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
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Generic kicker task that will run at each iteration and possibly block or manually kick any
 * dependent task.
 */
public abstract class KickerTask extends IncrementalTask {

    long buildId;

    @Input
    public long getBuildId() {
        return buildId;
    }

    File markerFile;

    @OutputFile
    @Optional
    public File getMarkerFile() {
        return markerFile;
    }
    InstantRunBuildContext instantRunContext;
    VariantScope variantScope;

    public abstract static class ConfigAction<T extends KickerTask>
            implements TaskConfigAction<T> {

        @NonNull
        protected final VariantScope scope;

        @NonNull
        protected final String name;

        public ConfigAction(@NonNull String name, @NonNull VariantScope scope) {
            this.scope = scope;
            this.name = name;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName(name);
        }

        @Override
        public void execute(@NonNull T task) {
            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.buildId = scope.getInstantRunBuildContext().getBuildId();
            task.variantScope = scope;
            task.instantRunContext = scope.getInstantRunBuildContext();
        }
    }
}
