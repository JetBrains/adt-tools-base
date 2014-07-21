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

import org.gradle.api.tasks.bundling.Zip;

import java.io.File;

/**
 * Output Data about a variant that produce a Library bundle (.aar)
 */
public class LibVariantOutputData extends BaseVariantOutputData {

    public Zip packageLibTask;

    LibVariantOutputData(
            @Nullable String densityFilter,
            @Nullable String abiFilter,
            @NonNull BaseVariantData variantData) {
        super(densityFilter, abiFilter, variantData);
    }

    @Override
    public void setOutputFile(@NonNull File file) {
        packageLibTask.setDestinationDir(file.getParentFile());
        packageLibTask.setArchiveName(file.getName());
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return packageLibTask.getArchivePath();
    }
}
