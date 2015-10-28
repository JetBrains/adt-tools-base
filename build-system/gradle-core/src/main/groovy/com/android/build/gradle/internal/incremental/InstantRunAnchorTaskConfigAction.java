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

import org.gradle.api.Task;

/**
 * Empty task used as an anchor task for all instant run related tasks. An anchor task can be used
 * to conveniently set dependencies.
 */
public class InstantRunAnchorTaskConfigAction implements TaskConfigAction<Task> {

    public static String getName(VariantScope scope) {
        return scope.getTaskName("incremental", "SupportDex");
    }

    private final String taskName;

    public InstantRunAnchorTaskConfigAction(VariantScope scope) {
        taskName = InstantRunAnchorTaskConfigAction.getName(scope);
    }

    @NonNull
    @Override
    public String getName() {
        return taskName;
    }

    @NonNull
    @Override
    public Class<Task> getType() {
        return Task.class;
    }

    @Override
    public void execute(@NonNull Task task) {
    }
}
