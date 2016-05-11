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

package com.android.build.gradle.internal.scope;

import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.tasks.PackageApplication;

/**
 * Partial implementation of the {@link InstantRunVariantScope} that contains generic implementation
 * of the interface for Gradle or an external build system.
 */
public abstract class GenericVariantScopeImpl implements InstantRunVariantScope {

    private AndroidTask<TransformTask> instantRunVerifierTask;

    @Override
    public AndroidTask<TransformTask> getInstantRunVerifierTask() {
        return instantRunVerifierTask;
    }

    @Override
    public void setInstantRunVerifierTask(AndroidTask<TransformTask> verifierTask) {
        instantRunVerifierTask = verifierTask;
    }

    private AndroidTask<TransformTask> instantRunSlicerTask;

    @Override
    public AndroidTask<TransformTask> getInstantRunSlicerTask() {
        return instantRunSlicerTask;
    }

    @Override
    public void setInstantRunSlicerTask(
            AndroidTask<TransformTask> instantRunSlicerTask) {
        this.instantRunSlicerTask = instantRunSlicerTask;
    }

    private AndroidTask<PackageApplication> packageApplicationTask;

    @Override
    public AndroidTask<PackageApplication> getPackageApplicationTask() {
        return packageApplicationTask;
    }

    @Override
    public void setPackageApplicationTask(
            AndroidTask<PackageApplication> packageApplicationTask) {
        this.packageApplicationTask = packageApplicationTask;
    }
}
