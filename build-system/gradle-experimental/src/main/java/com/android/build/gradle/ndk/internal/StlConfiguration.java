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

package com.android.build.gradle.ndk.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.ndk.Stl;
import com.android.build.gradle.internal.ndk.StlNativeToolSpecification;

import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.model.ModelMap;
import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;

/**
 * Configuration to setup STL for NDK.
 */
public class StlConfiguration {

    public static void createStlCopyTask(
            @NonNull ModelMap<Task> tasks,
            @NonNull final NativeBinarySpec binary,
            @NonNull final File buildDir,
            @NonNull NdkHandler ndkHandler,
            @NonNull Stl stl,
            @Nullable String stlVersion,
            @NonNull String buildTaskName) {
        if (!stl.isStatic()) {
            Abi abi = Abi.getByName(binary.getTargetPlatform().getName());
            final StlNativeToolSpecification stlConfig = ndkHandler.getStlNativeToolSpecification(
                    stl,
                    stlVersion,
                    abi);

            final String copyTaskName = NdkNamingScheme.getTaskName(binary, "copy", "StlSo");
            tasks.create(copyTaskName, Copy.class, copy -> {
                copy.from(stlConfig.getSharedLibs());
                copy.into(new File(buildDir,
                        NdkNamingScheme.getDebugLibraryDirectoryName(binary)));
            });

            tasks.named(buildTaskName, task -> {
                task.dependsOn(copyTaskName);
            });
        }
    }
}
