/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.internal.tasks.AbstractAndroidCompile
import com.android.build.gradle.internal.tasks.FileSupplier
import com.android.builder.core.AndroidBuilder
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.repository.FullRevision
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction
/**
 * Jack task.
 */
@ParallelizableTask
public class JackTask extends AbstractAndroidCompile
        implements FileSupplier, BinaryFileProviderTask {

    final static FullRevision JACK_MIN_REV = new FullRevision(21, 1, 0)

    AndroidBuilder androidBuilder

    boolean isVerbose

    boolean isDebugLog

    @InputFile
    File getJackExe() {
        new File(androidBuilder.targetInfo.buildTools.getPath(BuildToolInfo.PathId.JACK))
    }

    @InputFiles
    Collection<File> packagedLibraries

    @InputFiles
    @Optional
    Collection<File> proguardFiles

    @Input
    boolean debug

    File tempFolder

    @OutputFile
    File jackFile

    @OutputFile
    @Optional
    File mappingFile

    @Input
    boolean multiDexEnabled

    @Input
    int minSdkVersion

    @Input
    @Optional
    String javaMaxHeapSize

    @TaskAction
    void compile() {

        if (System.getenv("USE_JACK_API") == null ||
                !androidBuilder.convertByteCodeUsingJackApis(
                getDestinationDir(),
                getJackFile(),
                getClasspath().files,
                getPackagedLibraries(),
                getSource().files,
                getProguardFiles(),
                getMappingFile(),
                isMultiDexEnabled(),
                getMinSdkVersion())) {

            androidBuilder.convertByteCodeWithJack(
                    getDestinationDir(),
                    getJackFile(),
                    computeBootClasspath(),
                    getPackagedLibraries(),
                    computeEcjOptionFile(),
                    getProguardFiles(),
                    getMappingFile(),
                    isMultiDexEnabled(),
                    getMinSdkVersion(),
                    isDebugLog,
                    getJavaMaxHeapSize())
        }
    }

    private File computeEcjOptionFile() {
        File folder = getTempFolder()
        folder.mkdirs()
        File file = new File(folder, "ecj-options.txt");

        StringBuffer sb = new StringBuffer()

        for (File sourceFile : getSource().files) {
            sb.append(sourceFile.absolutePath).append('\n')
        }

        file.getParentFile().mkdirs()

        Files.write(sb.toString(), file, Charsets.UTF_8)

        return file
    }

    private String computeBootClasspath() {
        StringBuilder sb = new StringBuilder()

        boolean first = true;
        for (File file : getClasspath().files) {
            if (!first) {
                sb.append(':')
            } else {
                first = false
            }
            sb.append(file.getAbsolutePath())
        }

        return sb.toString()
    }

    @Override
    @NonNull
    BinaryFileProviderTask.Artifact getArtifact() {
        return new BinaryFileProviderTask.Artifact(
                BinaryFileProviderTask.BinaryArtifactType.JACK,
                getJackFile())
    }

    // ----- FileSupplierTask ----
    @NonNull
    @Override
    Task getTask() {
        return this
    }

    @Override
    File get() {
        return getMappingFile()
    }
}