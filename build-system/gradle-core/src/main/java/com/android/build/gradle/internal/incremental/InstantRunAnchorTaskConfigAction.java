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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;

import org.gradle.api.DefaultTask;

/**
 * Simple task used as an anchor task for all instant run related tasks. An anchor task can be used
 * to conveniently set dependencies.
 */
public class InstantRunAnchorTaskConfigAction implements TaskConfigAction<DefaultTask> {

    private final TransformVariantScope variantScope;

    /**
     * Creates a new anchor task with a dedicated prefix.
     */
    public InstantRunAnchorTaskConfigAction(@NonNull VariantScope scope) {
        this.variantScope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return variantScope.getTaskName("incremental", "Tasks");
    }

    @NonNull
    @Override
    public Class<DefaultTask> getType() {
        return DefaultTask.class;
    }

    @Override
    public void execute(@NonNull DefaultTask task) {
        task.setDescription("InstantRun task to build incremental artifacts");
    }
}
