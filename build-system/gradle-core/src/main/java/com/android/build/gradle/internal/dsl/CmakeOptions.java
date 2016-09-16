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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.model.CoreCmakeOptions;

import org.gradle.api.Project;

import java.io.File;

/**
 * DSL object for per-module CMake configurations, such as the path to your
 * <code>CMakeLists.txt</code> build script.
 */
public class CmakeOptions implements CoreCmakeOptions {
    @NonNull
    private final Project project;

    @Nullable
    private File path;

    public CmakeOptions(@NonNull Project project) {
        this.project = project;
    }

    /**
     * The relative path to your <code>CMakeLists.txt</code> build script.
     * <p>For example, if your
     * CMake build script is in the same folder as your module-level <code>build.gradle</code> file,
     * you simply pass the following:</p>
     * <p><code>path "CMakeLists.txt"</code></p>
     * <p>Gradle requires this build script to add your CMake project as a build dependency and pull
     * your native sources into your Android project.</p>
     */
    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@Nullable Object path) {
        this.path = project.file(path);
    }

    @Override
    public void setPath(@NonNull File path) {
        this.path = path;
    }
}
