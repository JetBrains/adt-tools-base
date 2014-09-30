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
import com.android.build.FilterData
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.tasks.BaseTask
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Callables
import com.google.common.util.concurrent.Futures
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

    ImmutableList<ApkOutputFile> mOutputFiles;

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
    public synchronized  ImmutableList<ApkOutputFile> getOutputSplitFiles() {
        if (mOutputFiles == null) {

            ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();

            if (getOutputPackagedSplitResListFile().exists()) {
                GsonBuilder gsonBuilder = new GsonBuilder();
                gsonBuilder.registerTypeAdapter(ApkOutputFile, new ApkOutputFile.JsonDeserializer())
                Gson gson = gsonBuilder.create()
                for (ApkOutputFile vo : gson.fromJson(
                        new FileReader(getOutputPackagedSplitResListFile()),
                        ApkOutputFile[].class)) {
                    builder.add(vo);
                }
            } else {
                // the project has not been built yet so we extrapolate what the package step result
                // might look like. So far, we only handle density splits, eventually we will need
                // to disambiguate.
                for (String split : splits) {
                    ApkOutputFile apkOutput = new ApkOutputFile(
                            com.android.build.OutputFile.OutputType.SPLIT,
                            ImmutableList.<FilterData>of(FilterData.Builder.build(com.android.build.OutputFile.DENSITY, split)),
                            "",
                            Callables.returning(new File(getOutputPackagedSplitResListFile().getParent(), split)))
                    builder.add(apkOutput)
                }
            }
            mOutputFiles = builder.build()
        }
        return mOutputFiles;
    }

    @TaskAction
    protected void doFullTaskAction() {

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ApkOutputFile,
                new ApkOutputFile.JsonDeserializer())
        Gson gson = gsonBuilder.create()

        ImmutableCollection.Builder<ApkOutputFile> tmpOutputs =
                ImmutableList.builder();

        ApkOutputFile[] variantOutputs = gson.fromJson(
                new FileReader(getInputSplitResListFile()), ApkOutputFile[].class)

        for (ApkOutputFile variantOutput : variantOutputs) {
            println "in package " + variantOutput
            String apkName = "${project.archivesBaseName}-${outputBaseName}-${variantOutput.splitIdentifiers}"
            apkName = apkName + (signingConfig == null
                    ? "-unsigned.apk"
                    : "-unaligned.apk")

            File outFile = new File(outputDirectory, apkName);
            getBuilder().signApk(variantOutput.getOutputFile(), signingConfig, outFile)
            tmpOutputs.add(new ApkOutputFile(
                    com.android.build.OutputFile.OutputType.SPLIT,
                    ImmutableList.<FilterData>of(FilterData.Builder.build(
                        com.android.build.OutputFile.DENSITY,
                        variantOutput.getFilterByType(
                                com.android.build.OutputFile.FilterType.DENSITY))),
                    variantOutput.suffix,
                    Callables.returning(outFile)))
        }

        FileWriter fileWriter = new FileWriter(outputPackagedSplitResListFile)
        fileWriter.write(gson.toJson(tmpOutputs.build().toArray()))
        fileWriter.close()
    }
}
