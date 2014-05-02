/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.api.ApplicationVariantImpl;
import com.android.builder.VariantConfiguration;

import org.gradle.api.Task;

/**
 */
public class ApplicationVariantFactory implements VariantFactory {

    @NonNull
    private final BasePlugin basePlugin;

    public ApplicationVariantFactory(@NonNull BasePlugin basePlugin) {
        this.basePlugin = basePlugin;
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(@NonNull VariantConfiguration variantConfiguration) {
        return new ApplicationVariantData(variantConfiguration);
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(@NonNull BaseVariantData variantData) {
        return basePlugin.getInstantiator().newInstance(ApplicationVariantImpl.class, variantData, basePlugin);
    }

    @NonNull
    @Override
    public VariantConfiguration.Type getVariantConfigurationType() {
        return VariantConfiguration.Type.DEFAULT;
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    /**
     * Creates the tasks for a given ApplicationVariantData.
     * @param variantData the non-null ApplicationVariantData.
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     */
    @Override
    public void createTasks(
            @NonNull BaseVariantData variantData,
            @Nullable Task assembleTask) {

        assert variantData instanceof ApplicationVariantData;
        ApplicationVariantData appVariantData = (ApplicationVariantData) variantData;

        basePlugin.createAnchorTasks(variantData);

        basePlugin.createCheckManifestTask(variantData);

        // Add a task to process the manifest(s)
        basePlugin.createProcessManifestTask(variantData, "manifests");

        // Add a task to create the res values
        basePlugin.createGenerateResValuesTask(variantData);

        // Add a task to compile renderscript files.
        basePlugin.createRenderscriptTask(variantData);

        // Add a task to merge the resource folders
        basePlugin.createMergeResourcesTask(variantData, true /*process9Patch*/);

        // Add a task to merge the asset folders
        basePlugin.createMergeAssetsTask(variantData, null /*default location*/, true /*includeDependencies*/);

        // Add a task to create the BuildConfig class
        basePlugin.createBuildConfigTask(variantData);

        // Add a task to generate resource source files
        basePlugin.createProcessResTask(variantData, true /*generateResourcePackage*/);

        // Add a task to process the java resources
        basePlugin.createProcessJavaResTask(variantData);

        basePlugin.createAidlTask(variantData, null /*parcelableDir*/);

        // Add a compile task
        basePlugin.createCompileTask(variantData, null/*testedVariant*/);

        // Add NDK tasks
        basePlugin.createNdkTasks(variantData);

        basePlugin.addPackageTasks(appVariantData, assembleTask, true /*publishApk*/);
    }
}
