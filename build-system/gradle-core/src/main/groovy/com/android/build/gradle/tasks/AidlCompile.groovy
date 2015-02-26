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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.annotations.concurrency.GuardedBy
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.incremental.DependencyData
import com.android.builder.internal.incremental.DependencyDataStore
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.res2.FileStatus
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.util.PatternSet

import java.util.concurrent.Callable

/**
 * Task to compile aidl files. Supports incremental update.
 */
public class AidlCompile extends IncrementalTask {

    private static final String DEPENDENCY_STORE = "dependency.store"

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File sourceOutputDir

    @OutputDirectory @Optional
    File aidlParcelableDir

    // ----- PRIVATE TASK API -----
    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    List<File> sourceDirs

    @InputFiles
    List<File> importDirs

    final PatternSet patternSet = new PatternSet().include("**/*.aidl")

    @InputFiles
    FileTree getSourceFiles() {
        FileTree src = null
        Set<File> sources = getSourceDirs()
        if (!sources.isEmpty()) {
            src = getProject().files(new ArrayList<Object>(sources)).getAsFileTree().matching(patternSet)
        }
        return src == null ? getProject().files().getAsFileTree() : src
    }

    private static class DepFileProcessor implements DependencyFileProcessor {

        @GuardedBy("this")
        List<DependencyData> dependencyDataList = Lists.newArrayList()

        List<DependencyData> getDependencyDataList() {
            return dependencyDataList
        }

        @Override
        DependencyData processFile(@NonNull File dependencyFile) {
            DependencyData data = DependencyData.parseDependencyFile(dependencyFile)
            if (data != null) {
                synchronized (this) {
                    dependencyDataList.add(data)
                }
            }

            return data;
        }
    }

    protected boolean isIncremental() {
        // TODO fix once dep file parsing is resolved.
        return false
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
    private void compileAllFiles(DependencyFileProcessor dependencyFileProcessor) {
        getBuilder().compileAllAidlFiles(
                getSourceDirs(),
                getSourceOutputDir(),
                getAidlParcelableDir(),
                getImportDirs(),
                dependencyFileProcessor)
    }

    /**
     * Returns the import folders.
     */
    @NonNull
    private List<File> getImportFolders() {
        List<File> fullImportDir = Lists.newArrayList()
        fullImportDir.addAll(getImportDirs())
        fullImportDir.addAll(getSourceDirs())

        return fullImportDir
    }

    /**
     * Compiles a single file.
     * @param sourceFolder the file to compile.
     * @param file the file to compile.
     * @param importFolders the import folders.
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    private void compileSingleFile(
            @NonNull File sourceFolder,
            @NonNull File file,
            @Nullable List<File> importFolders,
            @NonNull DependencyFileProcessor dependencyFileProcessor) {
        getBuilder().compileAidlFile(
                sourceFolder,
                file,
                getSourceOutputDir(),
                getAidlParcelableDir(),
                importFolders,
                dependencyFileProcessor)
    }

    @Override
    protected void doFullTaskAction() {
        // this is full run, clean the previous output
        File destinationDir = getSourceOutputDir()
        emptyFolder(destinationDir)

        File parcelableDir = getAidlParcelableDir()
        if (parcelableDir != null) {
            emptyFolder(parcelableDir)
        }

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
        } catch (Exception ignored) {
            incrementalData.delete()
            project.logger.info(
                    "Failed to read dependency store: full task run!")
            doFullTaskAction()
            return
        }

        final List<File> importFolders = getImportFolders()
        final DepFileProcessor processor = new DepFileProcessor()

        // use an executor to parallelize the compilation of multiple files.
        WaitableExecutor<Void> executor = new WaitableExecutor<Void>()

        Map<String,DependencyData> mainFileMap = store.getMainFileMap()

        for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
            FileStatus status = entry.getValue()

            switch (status) {
                case FileStatus.NEW:
                    executor.execute(new Callable<Void>() {
                        @Override
                        Void call() throws Exception {
                            File file = entry.getKey()
                            compileSingleFile(getSourceFolder(file), file, importFolders, processor)
                        }
                    })
                    break
                case FileStatus.CHANGED:
                    List<DependencyData> impactedData = inputMap.get(entry.getKey().absolutePath)
                    if (impactedData != null) {
                        final int count = impactedData.size();
                        for (int i = 0; i < count; i++) {
                            final DependencyData data = impactedData.get(i);

                            executor.execute(new Callable<Void>() {
                                @Override
                                Void call() throws Exception {
                                    File file = new File(data.getMainFile());
                                    compileSingleFile(getSourceFolder(file), file,
                                            importFolders, processor)
                                }
                            })
                        }
                    }
                    break
                case FileStatus.REMOVED:
                    final DependencyData data2 = mainFileMap.get(entry.getKey().absolutePath)
                    if (data2 != null) {
                        executor.execute(new Callable<Void>() {
                            @Override
                            Void call() throws Exception {
                                cleanUpOutputFrom(data2)
                            }
                        })
                        store.remove(data2)
                    }
                    break
            }
        }

        try {
            executor.waitForTasksWithQuickFail(true /*cancelRemaining*/)
        } catch (Throwable t) {
            incrementalData.delete()
            throw t
        }

        // get all the update data for the recompiled objects
        store.updateAll(processor.getDependencyDataList())

        store.saveTo(incrementalData)
    }

    private File getSourceFolder(@NonNull File file) {
        File parentDir = file
        while ((parentDir = parentDir.getParentFile()) != null) {
            for (File folder : getSourceDirs()) {
                if (parentDir.equals(folder)) {
                    return folder;
                }
            }
        }

        assert false
    }

    private static void cleanUpOutputFrom(@NonNull DependencyData dependencyData) {
        for (String output : dependencyData.getOutputFiles()) {
            new File(output).delete()
        }
        for (String output : dependencyData.getSecondaryOutputFiles()) {
            new File(output).delete()
        }
    }
}
