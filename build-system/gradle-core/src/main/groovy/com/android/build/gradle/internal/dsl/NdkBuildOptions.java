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

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.model.CoreNdkBuildOptions;

import java.io.File;

/**
 * Implementation for ndkBuild subsection of externalNativeBuild.
 *
 * android {
 *     externalNativeBuild {
 *         ndkBuild {
 *             path '...'
 *         }
 *     }
 * }
 */
public class NdkBuildOptions implements CoreNdkBuildOptions {
    @Nullable
    private File path;

    public NdkBuildOptions() {
    }

    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    @Override
    public void setPath(File path) {
        this.path = path;
    }
}
