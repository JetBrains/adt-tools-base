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

package com.android.build.gradle.internal.coverage

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Simple Jacoco instrument task that calls the Ant version.
 */
public class JacocoInstrumentTask extends DefaultTask {

    @InputDirectory
    File inputDir

    @OutputDirectory
    File outputDir

    /**
     * Classpath containing Jacoco classes for use by the task.
     */
    @InputFiles
    FileCollection jacocoClasspath

    @TaskAction
    void instrument() {
        File outDir = getOutputDir()
        outDir.deleteDir()
        outDir.mkdirs()

        getAnt().taskdef(name: 'instrumentWithJacoco',
                         classname: 'org.jacoco.ant.InstrumentTask',
                         classpath: getJacocoClasspath().asPath)
        getAnt().instrumentWithJacoco(destdir: outDir) {
            fileset(dir: getInputDir())
        }
    }
}
