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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.OutputFile.FilterType;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.build.gradle.internal.publishing.FilterDataPersistence;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.tasks.SplitFileSupplier;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.gradle.api.Task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Common code for all split related tasks
 */
public abstract class SplitRelatedTask extends BaseTask {

    @Nullable
    public abstract File getApkMetadataFile();

    /**
     * Calculates the list of output files, coming from the list of input files, mangling the output
     * file name.
     */
    public abstract List<ApkOutputFile> getOutputSplitFiles();

    /**
     * Returns the list of split information for this task. Each split is a unique combination of
     * filter type and identifier.
     */
    public abstract List<FilterData> getSplitsData();

    /**
     * Returns a list of {@link Supplier<File>} for each split APK file
     */
    public List<SplitFileSupplier> getOutputFileSuppliers() {
        ImmutableList.Builder<SplitFileSupplier> suppliers = ImmutableList.builder();
        for (final FilterData filterData : getSplitsData()) {

            ApkOutputFile outputFile =
                    Iterables.find(getOutputSplitFiles(), new Predicate<ApkOutputFile>() {
                        @Override
                        public boolean apply(ApkOutputFile apkOutputFile) {
                            return filterData.getIdentifier().equals(
                                    apkOutputFile.getFilter(filterData.getFilterType()));
                        }
                    });

            if (outputFile != null) {
                // make final references to not confused the groovy runtime...
                final File file = outputFile.getOutputFile();
                final FilterData data = filterData;

                suppliers.add(new SplitFileSupplier() {

                    @Override
                    public File get() {
                        return file;
                    }

                    @NonNull
                    @Override
                    public Task getTask() {
                        return SplitRelatedTask.this;
                    }

                    @NonNull
                    @Override
                    public FilterData getFilterData() {
                        return data;
                    }
                });
            }
        }
        return suppliers.build();
    }

    /**
     * Saves the APK metadata to the configured file.
     */
    protected void saveApkMetadataFile() throws IOException {

        File metadataFile = getApkMetadataFile();
        if (metadataFile == null) {
            return;
        }
        FileWriter fileWriter = null;
        try {
            metadataFile.getParentFile().mkdirs();
            fileWriter = new FileWriter(metadataFile);
            FilterDataPersistence persistence = new FilterDataPersistence();
            persistence.persist(getOutputFileSuppliers(), fileWriter);

        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    /**
     * Creates a new FilterData for each identifiers for a particular {@link FilterType} and store
     * it in the to builder.
     * @param to          the builder to store the new FilterData instances in.
     * @param identifiers the list of filter identifiers
     * @param filterType  the filter type.
     */
    protected static void addAllFilterData(ImmutableList.Builder<FilterData> to,
            Collection<String> identifiers,
            OutputFile.FilterType filterType) {
        for (String identifier : identifiers) {
            to.add(FilterDataImpl.build(filterType.toString(), identifier));
        }
    }
}
