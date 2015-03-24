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
package com.android.build.gradle.internal.tasks
import com.android.ide.common.res2.FileStatus
import com.android.ide.common.res2.SourceSet
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

public abstract class IncrementalTask extends BaseTask {

    @OutputDirectory @Optional
    File incrementalFolder

    /**
     * Whether this task can support incremental update.
     *
     * @return whether this task can support incremental update.
     */
    protected boolean isIncremental() {
        return false
    }

    /**
     * Actual task action. This is called when a full run is needed, which is always the case if
     * {@link #isIncremental()} returns false.
     *
     */
    protected abstract void doFullTaskAction()

    /**
     * Optional incremental task action.
     * Only used if {@link #isIncremental()} returns true.
     *
     * @param changedInputs the changed input files.
     */
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {
        // do nothing.
    }

    /**
     * Actual entry point for the action.
     * Calls out to the doTaskAction as needed.
     */
    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) {
        if (!isIncremental()) {
            doFullTaskAction()
            return
        }

        if (!inputs.isIncremental()) {
            project.logger.info("Unable do incremental execution: full task run")
            doFullTaskAction()
            return
        }

        Map<File, FileStatus> changedInputs = Maps.newHashMap()
        inputs.outOfDate { change ->
            //noinspection GroovyAssignabilityCheck
            changedInputs.put(change.file, change.isAdded() ? FileStatus.NEW : FileStatus.CHANGED)
        }

        inputs.removed { change ->
            //noinspection GroovyAssignabilityCheck
            changedInputs.put(change.file, FileStatus.REMOVED)
        }

        doIncrementalTaskAction(changedInputs)
    }

    public static List<File> flattenSourceSets(List<? extends SourceSet> resourceSets) {
        List<File> list = Lists.newArrayList()

        for (SourceSet sourceSet : resourceSets) {
            list.addAll(sourceSet.sourceFiles)
        }

        return list
    }
}
