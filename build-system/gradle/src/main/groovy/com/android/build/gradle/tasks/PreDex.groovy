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
import com.android.annotations.NonNull
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.DexOptions
import com.android.ide.common.internal.WaitableExecutor
import com.google.common.base.Charsets
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.util.concurrent.Callable

@ParallelizableTask
public class PreDex extends BaseTask {

    // ----- PUBLIC TASK API -----

    // ----- PRIVATE TASK API -----
    @Input
    String getBuildToolsVersion() {
        getBuildTools().getRevision()
    }

    // this is used automatically by Gradle, even though nothing
    // in the class uses it.
    @SuppressWarnings("GroovyUnusedDeclaration")
    @InputFiles
    Collection<File> inputFiles

    @OutputDirectory
    File outputFolder

    @Nested
    com.android.build.gradle.internal.dsl.DexOptions dexOptions

    @Input
    boolean multiDex

    @TaskAction
    void taskAction(IncrementalTaskInputs taskInputs) {

        final boolean multiDexEnabled = getMultiDex()

        final File outFolder = getOutputFolder()

        boolean incremental = taskInputs.isIncremental()
        // if we are not in incremental mode, then outOfDate will contain
        // all the files, but first we need to delete the previous output
        if (!incremental) {
            emptyFolder(outFolder)
        }

        final Set<String> hashs = Sets.newHashSet()
        final WaitableExecutor<Void> executor = new WaitableExecutor<Void>()
        final List<File> inputFileDetails = Lists.newArrayList()

        taskInputs.outOfDate { final change ->
            inputFileDetails.add(change.file)
        }

        for (final File file : inputFileDetails) {
            Callable<Void> action = new PreDexTask(outFolder, file, hashs,
                    multiDexEnabled);
            executor.execute(action);
        }

        if (incremental) {
            taskInputs.removed { change ->
                //noinspection GroovyAssignabilityCheck
                File preDexedFile = getDexFileName(outFolder, change.file)
                if (preDexedFile.isDirectory()) {
                    logger.info("deleteDir(" + preDexedFile + ") returned: " + preDexedFile.deleteDir())
                } else {
                    logger.info("delete(" + preDexedFile + ") returned: " + preDexedFile.delete())
                }
            }
        }

        executor.waitForTasksWithQuickFail(false)
    }

    private final class PreDexTask implements Callable<Void> {
        private final File outFolder
        private final File fileToProcess
        private final Set<String> hashs
        private final boolean multiDexEnabled
        private final DexOptions options = getDexOptions()
        private final AndroidBuilder builder = getBuilder()


        private PreDexTask(
                File outFolder,
                File file,
                Set<String> hashs,
                boolean multiDexEnabled) {
            this.outFolder = outFolder
            this.fileToProcess = file
            this.hashs = hashs
            this.multiDexEnabled = multiDexEnabled
        }

        @Override
        Void call() throws Exception {
            // TODO remove once we can properly add a library as a dependency of its test.
            String hash = getFileHash(fileToProcess)

            synchronized (hashs) {
                if (hashs.contains(hash)) {
                    return null
                }

                hashs.add(hash)
            }

            //noinspection GroovyAssignabilityCheck
            File preDexedFile = getDexFileName(outFolder, fileToProcess)

            if (multiDexEnabled) {
                preDexedFile.mkdirs()
            }

            //noinspection GroovyAssignabilityCheck
            builder.preDexLibrary(fileToProcess, preDexedFile, multiDexEnabled, options)

            return null
        }
    }

    /**
     * Returns the hash of a file.
     * @param file the file to hash
     * @return
     */
    private static String getFileHash(@NonNull File file) {
        HashCode hashCode = Files.hash(file, Hashing.sha1())
        return hashCode.toString()
    }

    /**
     * Returns a unique File for the pre-dexed library, even
     * if there are 2 libraries with the same file names (but different
     * paths)
     *
     * If multidex is enabled the return File is actually a folder.
     *
     * @param outFolder the output folder.
     * @param inputFile the library
     * @param multidex whether multidex is enabled.
     * @return
     */
    @NonNull
    static File getDexFileName(@NonNull File outFolder, @NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName()
        // remove the extension
        int pos = name.lastIndexOf('.')
        if (pos != -1) {
            name = name.substring(0, pos)
        }

        // add a hash of the original file path.
        String input = inputFile.getAbsolutePath();
        HashFunction hashFunction = Hashing.sha1()
        HashCode hashCode = hashFunction.hashString(input, Charsets.UTF_16LE)

        return new File(outFolder, name + "-" + hashCode.toString() + SdkConstants.DOT_JAR)
    }
}
