/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.utils.StringHelper;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;

/**
 * Class containing a BuildType and associated data (Sourceset for instance).
 */
public class BuildTypeData extends VariantDimensionData {
    private final CoreBuildType buildType;
    private AndroidTask<DefaultTask> assembleTask;

    BuildTypeData(
            @NonNull  CoreBuildType buildType,
            @NonNull  Project project,
            @NonNull  DefaultAndroidSourceSet sourceSet,
            @Nullable DefaultAndroidSourceSet unitTestSourceSet) {
        super(sourceSet, null, unitTestSourceSet, project);

        this.buildType = buildType;

        String sourceSetName = StringHelper.capitalize(buildType.getName());
    }

    public CoreBuildType getBuildType() {
        return buildType;
    }

    @Nullable
    public AndroidTask<DefaultTask> getAssembleTask() {
        return assembleTask;
    }

    public void setAssembleTask(AndroidTask<DefaultTask> assembleTask) {
        this.assembleTask = assembleTask;
    }
}
