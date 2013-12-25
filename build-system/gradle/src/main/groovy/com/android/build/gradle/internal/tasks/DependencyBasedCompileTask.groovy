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
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.incremental.DependencyData
import com.android.builder.internal.incremental.DependencyDataStore
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.res2.FileStatus
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.tasks.OutputDirectory

import java.util.concurrent.Callable
/**
 * Base task for source generators that generate and use dependency files.
 */
public abstract class DependencyBasedCompileTask extends IncrementalTask {

    private static final String DEPENDENCY_STORE = "dependency.store"

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File sourceOutputDir

    // ----- PRIVATE TASK API -----

    private static class DepFileProcessor implements DependencyFileProcessor {

        List<DependencyData> dependencyDataList = Lists.newArrayList()

        List<DependencyData> getDependencyDataList() {
            return dependencyDataList
        }

        @Override
        boolean processFile(@NonNull File dependencyFile) {
            DependencyData data = DependencyData.parseDependencyFile(dependencyFile)
            if (data != null) {
                synchronized (this) {
                    dependencyDataList.add(data)
                }
            }

            return true
        }
    }

    /**
     * Action methods to compile all the files.
     *
     * The method receives a {@link DependencyFileProcessor} to be used by the
     * {@link com.android.builder.internal.compiler.SourceSearcher.SourceFileProcessor} during
     * the compilation.
     *
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    protected abstract void compileAllFiles(DependencyFileProcessor dependencyFileProcessor)

    /**
     * Setup call back used once before calling multiple
     * {@link #compileSingleFile(File, Object, DependencyFileProcessor)}
     * during incremental compilation. The result object is passed back to the compileSingleFile
     * method
     *
     * @return an object or null.
     */
    protected abstract Object incrementalSetup()

    /**
     * Returns whether each changed file can be processed in parallel.
     */
    protected abstract boolean supportsParallelization()

    /**
     * Compiles a single file.
     * @param file the file to compile.
     * @param data the data returned by {@link #incrementalSetup()}
     * @param dependencyFileProcessor a DependencyFileProcessor
     *
     * @see #incrementalSetup()
     */
    protected abstract void compileSingleFile(@NonNull File file,
                                              @Nullable Object data,
                                              @NonNull DependencyFileProcessor dependencyFileProcessor)

    /**
     * Small wrapper around an optional WaitableExecutor.
     */
    private static final class ExecutorWrapper {
        WaitableExecutor executor = null

        ExecutorWrapper(boolean useExecutor) {
            if (useExecutor) {
                executor = new WaitableExecutor<Void>()
            }
        }

        void execute(Callable callable) throws Exception {
            if (executor != null) {
                executor.execute(callable)
            } else {
                callable.call()
            }
        }

        @Nullable
        List<WaitableExecutor.TaskResult<Void>> waitForTasks() {
            if (executor != null) {
                return executor.waitForAllTasks()
            }

            return null
        }
    }

    @Override
    protected void doFullTaskAction() {
        // this is full run, clean the previous output
        File destinationDir = getSourceOutputDir()
        emptyFolder(destinationDir)

        DepFileProcessor processor = new DepFileProcessor()

        compileAllFiles(processor)

        List<DependencyData> dataList = processor.getDependencyDataList()

        DependencyDataStore store = new DependencyDataStore()
        store.addData(dataList)

        store.saveTo(new File(getIncrementalFolder(), DEPENDENCY_STORE))
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) {

        File incrementalData = new File(getIncrementalFolder(), DEPENDENCY_STORE)
        DependencyDataStore store = new DependencyDataStore()
        Multimap<String, DependencyData> inputMap
        try {
            inputMap = store.loadFrom(incrementalData)
        } catch (Exception e) {
            project.logger.info(
                    "Failed to read dependency store: full task run!")
            doFullTaskAction()
            return
        }

        final Object incrementalObject = incrementalSetup()
        final DepFileProcessor processor = new DepFileProcessor()

        // use an executor to parallelize the compilation of multiple files.
        ExecutorWrapper executor = new ExecutorWrapper(supportsParallelization())

        Map<String,DependencyData> mainFileMap = store.getMainFileMap()

        for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue()

            switch (status) {
                case FileStatus.NEW:
                    executor.execute(new Callable<Void>() {
                        @Override
                        Void call() throws Exception {
                            compileSingleFile(entry.getKey(), incrementalObject, processor)
                        }
                    })
                    break
                case FileStatus.CHANGED:
                    List<DependencyData> impactedData = inputMap.get(entry.getKey().absolutePath)
                    if (impactedData != null) {
                        for (final DependencyData data : impactedData) {
                            executor.execute(new Callable<Void>() {
                                @Override
                                Void call() throws Exception {
                                    compileSingleFile(new File(data.getMainFile()),
                                            incrementalObject, processor)
                                }
                            })
                        }
                    }
                    break
                case FileStatus.REMOVED:
                    final DependencyData data = mainFileMap.get(entry.getKey().absolutePath)
                    if (data != null) {
                        executor.execute(new Callable<Void>() {
                            @Override
                            Void call() throws Exception {
                                cleanUpOutputFrom(data)
                            }
                        })
                        store.remove(data)
                    }
                    break
            }
        }

        // results will be null if there was no spawning of threads
        List<WaitableExecutor.TaskResult<Void>> results = executor.waitForTasks()
        if (results != null) {
            for (WaitableExecutor.TaskResult<Void> result : results) {
                if (result.exception != null) {
                    throw result.exception
                }
            }
        }

        // get all the update data for the recompiled objects
        store.updateAll(processor.getDependencyDataList())

        store.saveTo(incrementalData)
    }

    private static void cleanUpOutputFrom(DependencyData dependencyData) {
        List<String> outputs = dependencyData.getOutputFiles()

        for (String output : outputs) {
            new File(output).delete()
        }
    }
}
