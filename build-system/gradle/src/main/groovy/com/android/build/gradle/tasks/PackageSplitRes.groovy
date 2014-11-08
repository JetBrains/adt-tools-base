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
import com.android.build.OutputFile
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.dsl.SigningConfigDsl
import com.android.build.gradle.internal.tasks.BaseTask
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Callables
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Package each split resources into a specific signed apk file.
 */
class PackageSplitRes extends BaseTask {

    ImmutableList<ApkOutputFile> mOutputFiles;

    @Input
    File inputDirectory

    @OutputDirectory
    File outputDirectory

    @Input
    Set<String> splits

    @Input
    String outputBaseName

    @Nested @Optional
    SigningConfigDsl signingConfig

    @NonNull
    public synchronized  ImmutableList<ApkOutputFile> getOutputSplitFiles() {

        if (mOutputFiles == null) {
            ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();
            if (outputDirectory.exists() && outputDirectory.listFiles().length > 0) {
                final Pattern pattern = Pattern.compile(
                        "${project.archivesBaseName}-${outputBaseName}-([h|x|d|p|i|m]*)(.*)")
                for (File file : outputDirectory.listFiles()) {
                    Matcher matcher = pattern.matcher(file.getName());
                    if (matcher.matches()) {
                        builder.add(new ApkOutputFile(
                                OutputFile.OutputType.SPLIT,
                                ImmutableList.<FilterData> of(FilterData.Builder.build(
                                        OutputFile.DENSITY,
                                        matcher.group(1))),
                                Callables.returning(file)));
                    }
                }
            } else {
                // the project has not been built yet so we extrapolate what the package step result
                // might look like. So far, we only handle density splits, eventually we will need
                // to disambiguate.
                for (String split : splits) {
                    ApkOutputFile apkOutput = new ApkOutputFile(
                            OutputFile.OutputType.SPLIT,
                            ImmutableList.<FilterData>of(
                                    FilterData.Builder.build(OutputFile.DENSITY,
                                            "${project.archivesBaseName}-${outputBaseName}-${split}")),
                            Callables.returning(new File(outputDirectory, split)))
                    builder.add(apkOutput)
                }
            }
            mOutputFiles = builder.build()
        }
        return mOutputFiles;
    }

    @TaskAction
    protected void doFullTaskAction() {

        // resources- and .ap_ should be shared in a setting somewhere. see BasePlugin:1206
        final Pattern pattern = Pattern.compile(
                "resources-${outputBaseName}.ap__([h|x|d|p|i|m]*)(.*)")
        for (File file : inputDirectory.listFiles()) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                ApkOutputFile outputFile = new ApkOutputFile(
                        OutputFile.OutputType.SPLIT,
                        ImmutableList.<FilterData> of(FilterData.Builder.build(
                                OutputFile.DENSITY,
                                matcher.group(1))),
                        Callables.returning(file));

                String apkName = "${project.archivesBaseName}-${outputBaseName}-" +
                        "${outputFile.getSplitIdentifiers('-' as char)}"
                apkName = apkName + (signingConfig == null
                        ? "-unsigned.apk"
                        : "-unaligned.apk")

                File outFile = new File(outputDirectory, apkName);
                getBuilder().signApk(outputFile.getOutputFile(), signingConfig, outFile)
            }
        }
    }
}
