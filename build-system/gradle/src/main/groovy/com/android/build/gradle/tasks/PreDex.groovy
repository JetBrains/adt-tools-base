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
import com.android.build.gradle.internal.dsl.DexOptionsImpl
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.builder.AndroidBuilder
import com.android.builder.DexOptions
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

public class PreDex extends BaseTask {

    // ----- PUBLIC TASK API -----

    // ----- PRIVATE TASK API -----

    // this is used automatically by Gradle, even though nothing
    // in the class uses it.
    @SuppressWarnings("GroovyUnusedDeclaration")
    @InputFiles
    Iterable<File> inputFiles

    @OutputDirectory
    File outputFolder

    @Nested
    DexOptionsImpl dexOptions

    @TaskAction
    void taskAction(IncrementalTaskInputs taskInputs) {
        final File outFolder = getOutputFolder()
        final DexOptions options = getDexOptions()

        // if we are not in incremental mode, then outOfDate will contain
        // all th files, but first we need to delete the previous output
        if (!taskInputs.isIncremental()) {
            emptyFolder(outFolder)
        }

        AndroidBuilder builder = getBuilder()

        taskInputs.outOfDate { change ->

            //noinspection GroovyAssignabilityCheck
            File preDexedFile = getDexFileName(outFolder, change.file)
            //noinspection GroovyAssignabilityCheck
            builder.preDexLibrary(change.file, preDexedFile, options)
        }

        taskInputs.removed { change ->
            //noinspection GroovyAssignabilityCheck
            File preDexedFile = getDexFileName(outFolder, change.file)
            preDexedFile.delete()
        }
    }

    /**
     * Returns a unique File for the pre-dexed library, even
     * if there are 2 libraries with the same file names (but different
     * paths)
     *
     * @param outFolder
     * @param inputFile the library
     * @return
     */
    @NonNull
    private static File getDexFileName(@NonNull File outFolder, @NonNull File inputFile) {
        // get the filename
        String name = inputFile.getName();
        // remove the extension
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        // add a hash of the original file path
        HashFunction hashFunction = Hashing.md5();
        HashCode hashCode = hashFunction.hashString(inputFile.getAbsolutePath());

        return new File(outFolder, name + "-" + hashCode.toString() + SdkConstants.DOT_JAR);
    }
}
