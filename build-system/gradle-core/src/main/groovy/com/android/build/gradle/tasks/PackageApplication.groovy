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

import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.tasks.FileSupplierTask
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.builder.packaging.DuplicateFileException
import com.google.common.base.Supplier
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.tooling.BuildException

@ParallelizableTask
public class PackageApplication extends IncrementalTask implements FileSupplierTask {

    // ----- PUBLIC TASK API -----

    @InputFile
    File resourceFile

    @InputDirectory
    File dexFolder

    @InputFiles
    Collection<File> dexedLibraries

    @InputDirectory @Optional
    File javaResourceDir

    Set<File> jniFolders

    @OutputFile
    File outputFile

    @Input @Optional
    Set<String> abiFilters

    // ----- PRIVATE TASK API -----

    @InputFiles
    Set<File> packagedJars

    @Input
    boolean jniDebugBuild

    @Nested @Optional
    SigningConfig signingConfig

    @Nested
    PackagingOptions packagingOptions

    @InputFiles
    public FileTree getNativeLibraries() {
        FileTree src = null
        Set<File> folders = getJniFolders()
        if (!folders.isEmpty()) {
            src = getProject().files(new ArrayList<Object>(folders)).getAsFileTree()
        }
        return src == null ? getProject().files().getAsFileTree() : src
    }

    @Override
    protected void doFullTaskAction() {
        try {
            getBuilder().packageApk(
                    getResourceFile().absolutePath,
                    getDexFolder(),
                    getDexedLibraries(),
                    getPackagedJars(),
                    getJavaResourceDir()?.absolutePath,
                    getJniFolders(),
                    getAbiFilters(),
                    getJniDebugBuild(),
                    getSigningConfig(),
                    getPackagingOptions(),
                    getOutputFile().absolutePath)
        } catch (DuplicateFileException e) {
            def logger = getLogger()
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile().absolutePath)
            logger.error("\tPath in archive: " + e.archivePath)
            logger.error("\tOrigin 1: " + e.file1)
            logger.error("\tOrigin 2: " + e.file2)
            logger.error("You can ignore those files in your build.gradle:")
            logger.error("\tandroid {")
            logger.error("\t  packagingOptions {")
            logger.error("\t    exclude '$e.archivePath'")
            logger.error("\t  }")
            logger.error("\t}")
            throw new BuildException(e.getMessage(), e);
        } catch (Exception e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    // ----- FileSupplierTask -----

    @Override
    File get() {
        return getOutputFile()
    }

    @Override
    Task getTask() {
        return this
    }
}
