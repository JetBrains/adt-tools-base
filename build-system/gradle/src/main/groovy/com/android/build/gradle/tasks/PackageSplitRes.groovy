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
import com.android.build.gradle.api.ApkOutput
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.tasks.BaseTask
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Package each split resources into a specific signed apk file.
 */
class PackageSplitRes extends BaseTask {

    ImmutableList<ApkOutput> mOutputFiles;

    @Input
    File inputDirectory

    @OutputDirectory
    File outputDirectory

    @Nested
    Set<String> splits

    @Input
    String outputBaseName

    @Input
    File inputSplitResListFile

    @Nested @Optional
    SigningConfigDsl signingConfig

    @OutputFile
    File outputPackagedSplitResListFile

    @NonNull
    public synchronized  ImmutableList<ApkOutput> getOutputFiles() {
        if (mOutputFiles == null) {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(ApkOutput.SplitApkOutput,
                    new ApkOutput.SplitApkOutput.JsonDeserializer())
            Gson gson = gsonBuilder.create()

            ImmutableList.Builder<ApkOutput> builder = ImmutableList.builder();

            for (ApkOutput vo : gson.fromJson(
                    new FileReader(getOutputPackagedSplitResListFile()),
                    ApkOutput.SplitApkOutput[].class)) {
                builder.add(vo);
            }
            mOutputFiles = builder.build()
        }
        return mOutputFiles;
    }

    @TaskAction
    protected void doFullTaskAction() {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ApkOutput.SplitApkOutput,
                new ApkOutput.SplitApkOutput.JsonDeserializer())
        Gson gson = gsonBuilder.create()

        ImmutableCollection.Builder<ApkOutput> tmpOutputs =
                ImmutableList.builder();

        ApkOutput.SplitApkOutput[] variantOutputs = gson.fromJson(
                new FileReader(getInputSplitResListFile()), ApkOutput.SplitApkOutput[].class)

        for (ApkOutput.SplitApkOutput variantOutput : variantOutputs) {
            println "in package " + variantOutput
            String apkName = "${project.archivesBaseName}-${outputBaseName}-${variantOutput.splitIdentifier}"
            apkName = apkName + (signingConfig == null
                    ? "-unsigned.apk"
                    : "-unaligned.apk")

            File outFile = new File(outputDirectory, apkName);
            getBuilder().signApk(variantOutput.getOutputFile(), signingConfig, outFile)
            tmpOutputs.add(new ApkOutput.SplitApkOutput(
                    ApkOutput.OutputType.SPLIT,
                    ApkOutput.SplitType.DENSITY,
                    variantOutput.splitIdentifier,
                    variantOutput.splitSuffix,
                    outFile))
        }

        FileWriter fileWriter = new FileWriter(outputPackagedSplitResListFile)
        fileWriter.write(gson.toJson(tmpOutputs.build().toArray()))
        fileWriter.close()
    }
}
