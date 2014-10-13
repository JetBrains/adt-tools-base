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

import com.android.build.gradle.internal.tasks.OutputFileTask
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Task to zip align all the splits
 */
class SplitZipAlign extends DefaultTask implements OutputFileTask{

    @InputDirectory
    File inputDirectory;

    @Input
    String outputBaseName;

    @OutputDirectory
    File outputFile;

    @InputFile
    File zipAlignExe

    @TaskAction
    void splitZipAlign() {

        final Pattern unalignedPattern = Pattern.compile(
                "${project.archivesBaseName}-${outputBaseName}-(.*)-unaligned.apk")
        final Pattern unsignedPattern = Pattern.compile(
                "${project.archivesBaseName}-${outputBaseName}-(.*)-unsigned.apk")

        for (File file : inputDirectory.listFiles()) {
            Matcher unaligned = unalignedPattern.matcher(file.getName())
            if (unaligned.matches()) {
                File out = new File(getOutputFile(),
                        "${project.archivesBaseName}_${outputBaseName}_${unaligned.group(1)}.apk")
                project.exec {
                    executable = getZipAlignExe()
                    args '-f', '4'
                    args file.absolutePath
                    args out
                }
            } else {
                Matcher unsigned = unsignedPattern.matcher(file.getName())
                if (unsigned.matches()) {
                    File out = new File(getOutputFile(),
                            "${project.archivesBaseName}_${outputBaseName}_${unsigned.group(1)}.apk")
                    project.exec {
                        executable = getZipAlignExe()
                        args '-f', '4'
                        args file.absolutePath
                        args out
                    }
                }
            }
        }
    }
}
