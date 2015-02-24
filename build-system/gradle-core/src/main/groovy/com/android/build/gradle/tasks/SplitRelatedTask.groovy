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

package com.android.build.gradle.tasks

import com.android.build.FilterData
import com.android.build.OutputFile
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.model.FilterDataImpl
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.FileSupplier
import com.google.common.base.Supplier
import com.google.common.collect.ImmutableList
import org.gradle.api.Task

/**
 * Common code for all split related tasks
 */
abstract class SplitRelatedTask extends BaseTask {

    /**
     * Calculates the list of output files, coming from the list of input files, mangling the
     * output file name.
     */
    public abstract List<ApkOutputFile> getOutputSplitFiles()

    /**
     * Returns the list of split information for this task. Each split is a unique combination of
     * filter type and identifier.
     */
    abstract List<FilterData> getSplitsData()

    /**
     * Returns a list of {@link Supplier<File>} for each split APK file
     */
    List<FileSupplier> getOutputFileSuppliers() {
        ImmutableList.Builder<FileSupplier> suppliers = ImmutableList.builder();
        for (FilterData filterData : getSplitsData()) {
            suppliers.add(new FileSupplier() {

                @Override
                File get() {
                    return getOutputSplitFiles().find({
                        filterData.identifier.equals(it.getFilter(filterData.filterType))
                    }).getOutputFile()
                }

                @Override
                Task getTask() {
                    return SplitRelatedTask.this
                }
            })
        }
        return suppliers.build()
    }

    /**
     * Creates a new FilterData for each identifiers for a particular {@link FilterType} and store
     * it in the to builder.
     * @param to the builder to store the new FilterData instances in.
     * @param identifiers the list of filter identifiers
     * @param filterType the filter type.
     */
    protected static void addAllFilterData(ImmutableList.Builder<FilterData> to,
            Collection<String> identifiers,
            OutputFile.FilterType filterType) {
        for (String identifier : identifiers) {
            to.add(FilterDataImpl.build(filterType.toString(), identifier))
        }
    }
}
