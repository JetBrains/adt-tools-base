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
import com.android.build.gradle.internal.model.FilterDataImpl
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Callables
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
class SplitZipAlign extends DefaultTask {

    @InputDirectory
    File inputDirectory;

    @Input
    String outputBaseName;

    @Input
    Set<String> densityFilters;

    @Input
    Set<String> abiFilters;

    @Input
    Set<String> languageFilters;

    @OutputDirectory
    File outputDirectory;

    @InputFile
    File zipAlignExe

    ImmutableList<ApkOutputFile> mOutputFiles;

    @NonNull
    public synchronized  ImmutableList<ApkOutputFile> getOutputSplitFiles() {

        if (mOutputFiles == null) {
            ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();
            processDensityFilters(densityFilters, builder);
            processFilters(abiFilters, OutputFile.FilterType.ABI, builder);
            processFilters(languageFilters, OutputFile.FilterType.LANGUAGE, builder);
            mOutputFiles = builder.build();
        }
        return mOutputFiles;
    }

    /**
     * Process density filters which can have some suffix appended after the filter identifier.
     */
    private void processDensityFilters(Set<String> filters,
            ImmutableList.Builder<ApkOutputFile> builder) {
        if (filters != null) {
            for (String filter : filters) {
                String fileName = "${project.archivesBaseName}-${outputBaseName}_${filter}"
                Optional<File> outputFile = findFileStartingWithName(outputDirectory, fileName);
                if (outputFile.isPresent()) {
                    List<FilterData> filtersData = ImmutableList.of(
                            FilterDataImpl.Builder.build(OutputFile.FilterType.DENSITY.name(), filter))
                    builder.add(new ApkOutputFile(
                            OutputFile.OutputType.SPLIT,
                            filtersData,
                            Callables.returning(outputFile.get())));
                }
            }
        }
    }

    /**
     * Returns a file starting with the provided file name prefix.
     * @param directory a directory where the file could be located.
     * @param name the file prefix.
     * @return the file if found of {@link Optional#absent()} if not.
     */
    private static Optional<File> findFileStartingWithName(File directory, String name) {
        for (File file : directory.listFiles()) {
            if (file.getName().startsWith(name)) {
                return Optional.of(file);
            }
        }
        return Optional.absent();
    }



    private void processFilters(Set<String> filters, OutputFile.FilterType filterType,
            ImmutableList.Builder<ApkOutputFile> builder) {
        if (filters != null) {
            for (String filter : filters) {
                String fileName = "${project.archivesBaseName}-${outputBaseName}_${filter}.apk"
                File outputFile = new File(outputDirectory, fileName);
                List<FilterData> filtersData = ImmutableList.of(
                        FilterDataImpl.Builder.build(filterType.name(), filter))
                builder.add(new ApkOutputFile(
                        OutputFile.OutputType.SPLIT,
                        filtersData,
                        Callables.returning(outputFile)));
            }
        }
    }

    @TaskAction
    void splitZipAlign() {

        Pattern unalignedPattern = Pattern.compile(
                "${project.archivesBaseName}-${outputBaseName}_(.*)-unaligned.apk")
        Pattern unsignedPattern = Pattern.compile(
                "${project.archivesBaseName}-${outputBaseName}_(.*)-unsigned.apk")

        for (File file : inputDirectory.listFiles()) {
            Matcher unaligned = unalignedPattern.matcher(file.getName())
            if (unaligned.matches()) {
                File out = new File(getOutputDirectory(),
                        "${project.archivesBaseName}-${outputBaseName}_${unaligned.group(1)}.apk")
                project.exec {
                    executable = getZipAlignExe()
                    args '-f', '4'
                    args file.absolutePath
                    args out
                }
            } else {
                Matcher unsigned = unsignedPattern.matcher(file.getName())
                if (unsigned.matches()) {
                    File out = new File(getOutputDirectory(),
                            "${project.archivesBaseName}-${outputBaseName}_${unsigned.group(1)}.apk")
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
