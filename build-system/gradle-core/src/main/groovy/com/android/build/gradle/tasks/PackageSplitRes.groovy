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
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.model.FilterDataImpl
import com.android.build.gradle.internal.tasks.BaseTask
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Callables
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Package each split resources into a specific signed apk file.
 */
@ParallelizableTask
class PackageSplitRes extends BaseTask {

    @Input
    File inputDirectory

    @OutputDirectory
    File outputDirectory

    @Input
    Set<String> densitySplits

    @Input
    Set<String> languageSplits

    @Input
    String outputBaseName

    @Nested @Optional
    SigningConfig signingConfig

    @OutputFiles
    public List<File> getOutputFiles() {
        return getOutputSplitFiles()*.getOutputFile();
    }

    @NonNull
    public ImmutableList<ApkOutputFile> getOutputSplitFiles() {

        // ABI splits are treated in PackageSplitAbi task.
        ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();
        if (outputDirectory.exists() && outputDirectory.listFiles().length > 0) {
            File[] potentialFiles = outputDirectory.listFiles();
            for (String density : densitySplits) {
                String filePath = "${project.archivesBaseName}-${outputBaseName}_${density}";
                for (File potentialFile : potentialFiles) {
                    // density related APKs have a suffix.
                   if (potentialFile.getName().startsWith(filePath)) {
                       builder.add(new ApkOutputFile(
                               OutputFile.OutputType.SPLIT,
                               ImmutableList.<FilterData> of(FilterDataImpl.Builder.build(
                                       OutputFile.DENSITY,
                                       density)),
                               Callables.returning(potentialFile)));
                   }
                }
            }
        } else {
            // the project has not been built yet so we extrapolate what the package step result
            // might look like. So far, we only handle density splits, eventually we will need
            // to disambiguate.
            addAllSplits(densitySplits, OutputFile.FilterType.DENSITY, builder);
        }
        // now do languages.
        addAllSplits(languageSplits, OutputFile.FilterType.LANGUAGE, builder);
        return builder.build()
    }

    @TaskAction
    protected void doFullTaskAction() {

        Pattern resourcePattern = Pattern.compile(
                "resources-${outputBaseName}.ap__(.*)")

        // resources- and .ap_ should be shared in a setting somewhere. see BasePlugin:1206
        for (File file : inputDirectory.listFiles()) {
            Matcher matcher = resourcePattern.matcher(file.getName());
            if (matcher.matches() && !matcher.group(1).isEmpty()) {
                File outFile = new File(outputDirectory, getOuputFileNameForSplit(matcher.group(1)));
                getBuilder().signApk(file, signingConfig, outFile)
            }
        }
    }

    private void addAllSplits(Collection<String> filters,
            OutputFile.FilterType filterType,
            ImmutableList.Builder<ApkOutputFile> builder) {
        for (String filter : filters) {
            ApkOutputFile apkOutput = new ApkOutputFile(
                    OutputFile.OutputType.SPLIT,
                    ImmutableList.<FilterData>of(
                            FilterDataImpl.Builder.build(filterType.name(), filter)),
                    Callables.returning(
                            new File(outputDirectory, getOuputFileNameForSplit(filter))))
            builder.add(apkOutput)
        }
    }

    private String getOuputFileNameForSplit(String split) {
        String apkName = "${project.archivesBaseName}-${outputBaseName}_${split}"
        return apkName + (signingConfig == null ? "-unsigned.apk" : "-unaligned.apk")
    }
}
