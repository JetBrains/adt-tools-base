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
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.model.FilterDataImpl
import com.android.build.gradle.internal.tasks.BaseTask
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.Callables
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Package a abi dimension specific split APK
 */
class PackageSplitAbi extends BaseTask {

    ImmutableList<ApkOutputFile> outputFiles;

    @InputFiles
    Collection<File> inputFiles

    File outputDirectory

    @Input
    Set<String> splits

    @Input
    String outputBaseName

    @Input
    boolean jniDebuggable

    @Nested @Optional
    SigningConfig signingConfig

    @Nested
    PackagingOptions packagingOptions

    @Input
    Collection<File> jniFolders;

    @OutputFiles
    public List<File> getOutputFiles() {
        return getOutputSplitFiles()*.getOutputFile();
    }

    @NonNull
    public synchronized  ImmutableList<ApkOutputFile> getOutputSplitFiles() {

        if (outputFiles == null) {
            ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();
            for (String split : splits) {
                String apkName = getApkName(split);
                ApkOutputFile apkOutput = new ApkOutputFile(
                        OutputFile.OutputType.SPLIT,
                        ImmutableList.<FilterData> of(
                                FilterDataImpl.Builder.build(OutputFile.ABI, apkName)),
                        Callables.returning(new File(outputDirectory, apkName)))
                builder.add(apkOutput)
            }
            outputFiles = builder.build()
        }
        return outputFiles;
    }

    private boolean isAbiSplit(String fileName) {
        for (String abi : getSplits()) {
            if (fileName.contains(abi)) {
                return true;
            }
        }
        return false;
    }

    @TaskAction
    protected void doFullTaskAction() {

        // resources- and .ap_ should be shared in a setting somewhere. see BasePlugin:1206
        final Pattern pattern = Pattern.compile(
                "resources-${getOutputBaseName()}-(.*).ap_")
        List<String> unprocessedSplits = new ArrayList(splits);
        for (File file : inputFiles) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches() && isAbiSplit(file.getName())) {
                String apkName = getApkName(matcher.group(1))

                File outFile = new File(getOutputDirectory(), apkName);
                getBuilder().packageApk(
                        file.absolutePath,
                        null, /* dexFolder */
                        null, /* dexedLibraries */
                        ImmutableList.of(),
                        null, /* getJavaResourceDir */
                        getJniFolders(),
                        ImmutableSet.of(matcher.group(1)),
                        getJniDebuggable(),
                        getSigningConfig(),
                        getPackagingOptions(),
                        outFile.absolutePath)
                unprocessedSplits.remove(matcher.group(1));
            }
        }
        if (!unprocessedSplits.isEmpty()) {
            String message = String.format("Could not find resource package for %1$s",
                    Joiner.on(',').join(unprocessedSplits));
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    private String getApkName(String split) {
        String apkName = "${project.archivesBaseName}-${getOutputBaseName()}_${split}"
        return apkName + (getSigningConfig() == null
                ? "-unsigned.apk"
                : "-unaligned.apk")
    }
}
