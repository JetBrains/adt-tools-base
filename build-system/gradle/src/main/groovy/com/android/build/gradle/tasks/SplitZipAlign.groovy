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

import com.android.build.gradle.api.ApkOutput
import com.android.build.gradle.internal.tasks.OutputFileTask
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task to zip align all the splits
 */
class SplitZipAlign extends DefaultTask implements OutputFileTask{

    @InputFile
    File packagedSplitResListFile

    @Input
    String outputBaseName;

    @OutputDirectory
    File outputFile;

    @OutputFile
    File alignedFileList

    @InputFile
    File zipAlignExe

    @TaskAction
    void splitZipAlign() {

        ImmutableList<ApkOutput.SplitApkOutput> splitVariantOutputs = ApkOutput.load(getPackagedSplitResListFile());

        ImmutableCollection.Builder<ApkOutput> tmpOutputs =
                ImmutableList.builder();
        for (ApkOutput.SplitApkOutput splitVariantOutput : splitVariantOutputs) {
            File out = new File(getOutputFile(),
                    "${project.archivesBaseName}_${outputBaseName}_${splitVariantOutput.splitIdentifier}.apk")
            project.exec {
                executable = getZipAlignExe()
                args '-f', '4'
                args splitVariantOutput.getOutputFile()
                args out
            }

            tmpOutputs.add(new ApkOutput.SplitApkOutput(
                    ApkOutput.OutputType.SPLIT,
                    ApkOutput.SplitType.DENSITY,
                    splitVariantOutput.splitIdentifier,
                    splitVariantOutput.splitSuffix,
                    out))
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create()
        FileWriter fileWriter = new FileWriter(alignedFileList)
        fileWriter.write(gson.toJson(tmpOutputs.build().toArray()))
        fileWriter.close()
    }
}
