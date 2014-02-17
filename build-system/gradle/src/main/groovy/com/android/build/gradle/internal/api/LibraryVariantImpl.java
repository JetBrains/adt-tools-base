/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import org.gradle.api.tasks.bundling.Zip;

import java.io.File;

/**
 * implementation of the {@link LibraryVariant} interface around a
 * {@link LibraryVariantData} object.
 */
public class LibraryVariantImpl extends BaseVariantImpl implements LibraryVariant {

    @NonNull
    private final LibraryVariantData variantData;
    @Nullable
    private TestVariant testVariant = null;

    public LibraryVariantImpl(@NonNull LibraryVariantData variantData) {
        this.variantData = variantData;
    }

    @Override
    @NonNull
    protected BaseVariantData getVariantData() {
        return variantData;
    }

    public void setTestVariant(@Nullable TestVariant testVariant) {
        this.testVariant = testVariant;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return variantData.packageLibTask.getArchivePath();
    }

    @Override
    public void setOutputFile(@NonNull File outputFile) {
        variantData.packageLibTask.setDestinationDir(outputFile.getParentFile());
        variantData.packageLibTask.setArchiveName(outputFile.getName());
    }

    @Override
    @Nullable
    public TestVariant getTestVariant() {
        return testVariant;
    }

    @Override
    public Zip getPackageLibrary() {
        return variantData.packageLibTask;
    }
}
