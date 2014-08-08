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
package com.android.build.gradle.tasks
import com.android.SdkConstants
import com.android.build.gradle.internal.dsl.DexOptionsImpl
import com.android.build.gradle.internal.tasks.BaseTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.util.concurrent.atomic.AtomicBoolean

public class Dex extends BaseTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File outputFolder

    @Input @Optional
    List<String> additionalParameters

    boolean enableIncremental = true

    // ----- PRIVATE TASK API -----

    @InputFiles
    Collection<File> inputFiles

    @InputFiles
    Collection<File> libraries

    @Nested
    DexOptionsImpl dexOptions

    @Input
    boolean multiDex = false

    File tmpFolder

    /**
     * Actual entry point for the action.
     * Calls out to the doTaskAction as needed.
     */
    @TaskAction
    void taskAction(IncrementalTaskInputs inputs) {
        if (!dexOptions.incremental || !enableIncremental) {
            doTaskAction(false /*incremental*/)
            return
        }

        if (!inputs.isIncremental()) {
            project.logger.info("Unable to do incremental execution: full task run.")
            doTaskAction(false /*incremental*/)
            return
        }

        AtomicBoolean forceFullRun = new AtomicBoolean()

        //noinspection GroovyAssignabilityCheck
        inputs.outOfDate { change ->
            // force full dx run if existing jar file is modified
            // New jar files are fine.
            if (change.isModified() && change.file.path.endsWith(SdkConstants.DOT_JAR)) {
                project.logger.info("Force full dx run: Found updated ${change.file}")
                forceFullRun.set(true)
            }
        }

        //noinspection GroovyAssignabilityCheck
        inputs.removed { change ->
            // force full dx run if existing jar file is removed
            if (change.file.path.endsWith(SdkConstants.DOT_JAR)) {
                project.logger.info("Force full dx run: Found removed ${change.file}")
                forceFullRun.set(true)
            }
        }

        doTaskAction(!forceFullRun.get())
    }

    private void doTaskAction(boolean incremental) {
        File outFolder = getOutputFolder()
        if (!incremental) {
            emptyFolder(outFolder)
        }

        File tmpFolder = getTmpFolder()
        tmpFolder.mkdirs()

        getBuilder().convertByteCode(
                getInputFiles(),
                getLibraries(),
                outFolder,
                getMultiDex(),
                getDexOptions(),
                getAdditionalParameters(),
                tmpFolder,
                incremental)
    }
}
