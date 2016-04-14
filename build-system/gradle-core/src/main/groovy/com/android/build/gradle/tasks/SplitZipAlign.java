/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.OutputFile.FilterType.ABI;
import static com.android.build.OutputFile.FilterType.DENSITY;
import static com.android.build.OutputFile.FilterType.LANGUAGE;
import static com.android.sdklib.BuildToolInfo.PathId.ZIP_ALIGN;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile.FilterType;
import com.android.build.OutputFile.OutputType;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.sdk.TargetInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;

import org.gradle.api.Action;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task to zip align all the splits
 */
@ParallelizableTask
public class SplitZipAlign extends SplitRelatedTask {

    private List<File> densityOrLanguageInputFiles = new ArrayList<File>();

    private List<File> abiInputFiles = new ArrayList<File>();

    private String outputBaseName;

    private Set<String> densityFilters;

    private Set<String> abiFilters;

    private Set<String> languageFilters;

    private File outputDirectory;

    private File zipAlignExe;

    private boolean useOldPackaging;

    @Nullable
    private File apkMetadataFile;

    @InputFiles
    public List<File> getDensityOrLanguageInputFiles() {
        return densityOrLanguageInputFiles;
    }

    @InputFiles
    public List<File> getAbiInputFiles() {
        return abiInputFiles;
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    public void setOutputBaseName(String outputBaseName) {
        this.outputBaseName = outputBaseName;
    }

    @Input
    public Set<String> getDensityFilters() {
        return densityFilters;
    }

    public void setDensityFilters(Set<String> densityFilters) {
        this.densityFilters = densityFilters;
    }

    @Input
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    @Input
    public Set<String> getLanguageFilters() {
        return languageFilters;
    }

    public void setLanguageFilters(Set<String> languageFilters) {
        this.languageFilters = languageFilters;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @InputFile
    public File getZipAlignExe() {
        return zipAlignExe;
    }

    public void setZipAlignExe(File zipAlignExe) {
        this.zipAlignExe = zipAlignExe;
    }

    @Override
    @OutputFile
    @Nullable
    public File getApkMetadataFile() {
        return apkMetadataFile;
    }

    public void setApkMetadataFile(@Nullable File apkMetadataFile) {
        this.apkMetadataFile = apkMetadataFile;
    }

    @OutputFiles
    public List<File> getOutputFiles() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (ApkOutputFile outputFile : getOutputSplitFiles()) {
            builder.add(outputFile.getOutputFile());
        }
        return builder.build();
    }

    @NonNull
    public List<File> getInputFiles() {
        return ImmutableList.copyOf(
                Iterables.concat(getDensityOrLanguageInputFiles(), getAbiInputFiles()));
    }

    @Override
    @NonNull
    public synchronized ImmutableList<ApkOutputFile> getOutputSplitFiles() {
        final String archivesBaseName = (String)getProject().getProperties().get("archivesBaseName");

        final ImmutableList.Builder<ApkOutputFile> outputFiles = ImmutableList.builder();
        InputProcessor addingLogic = new InputProcessor() {
            @Override
            public void process(String split, File file) {
                outputFiles.add(new ApkOutputFile(
                        OutputType.SPLIT,
                        ImmutableList.of(
                                FilterDataImpl.build(
                                        getFilterType(split).toString(),
                                        getFilter(split))),
                        Callables.returning(
                                new File(
                                        outputDirectory,
                                        archivesBaseName + "-" + outputBaseName + "_" + split
                                                + ".apk"))));

            }
        };

        forEachUnalignedInput(addingLogic);
        forEachUnsignedInput(addingLogic);
        return outputFiles.build();
    }

    public FilterType getFilterType(String filter) {
        String languageName = PackageSplitRes.unMangleSplitName(filter);
        if (languageFilters.contains(languageName)) {
            return FilterType.LANGUAGE;
        }

        if (abiFilters.contains(filter)) {
            return FilterType.ABI;
        }

        return FilterType.DENSITY;
    }

    public String getFilter(String filterWithPossibleSuffix) {
        FilterType type = getFilterType(filterWithPossibleSuffix);
        if (type == FilterType.DENSITY) {
            for (String density : densityFilters) {
                if (filterWithPossibleSuffix.startsWith(density)) {
                    return density;
                }
            }
        }

        if (type == FilterType.LANGUAGE) {
            return PackageSplitRes.unMangleSplitName(filterWithPossibleSuffix);
        }
        return filterWithPossibleSuffix;
    }

    /**
     * Returns true if the passed string is one of the filter we must process potentially followed
     * by a prefix (some density filters get V4, V16, etc... appended).
     */
    public boolean isFilter(String potentialFilterWithSuffix) {
        for (String density : densityFilters) {
            if (potentialFilterWithSuffix.startsWith(density)) {
                return true;
            }
        }

        return abiFilters.contains(potentialFilterWithSuffix)
                || languageFilters.contains(
                        PackageSplitRes.unMangleSplitName(potentialFilterWithSuffix));

    }

    private interface InputProcessor {
        void process(String split, File file);
    }

    private void forEachUnalignedInput(InputProcessor processor) {
        String archivesBaseName = (String)getProject().getProperties().get("archivesBaseName");
        Pattern unalignedPattern = Pattern.compile(
                archivesBaseName + "-" + outputBaseName + "_(.*)-unaligned.apk");

        for (File file : getInputFiles()) {
            Matcher unaligned = unalignedPattern.matcher(file.getName());
            if (unaligned.matches() && isFilter(unaligned.group(1))) {
                processor.process(unaligned.group(1), file);
            }
        }
    }

    private void forEachUnsignedInput(InputProcessor processor) {
        String archivesBaseName = (String)getProject().getProperties().get("archivesBaseName");
        Pattern unsignedPattern = Pattern.compile(
                archivesBaseName + "-" + outputBaseName + "_(.*)-unsigned.apk");

        for (File file : getInputFiles()) {
            Matcher unsigned = unsignedPattern.matcher(file.getName());
            if (unsigned.matches() && isFilter(unsigned.group(1))) {
                processor.process(unsigned.group(1), file);
            }

        }

    }

    @TaskAction
    public void splitZipAlign() throws IOException {
        final String archivesBaseName = (String)getProject().getProperties().get("archivesBaseName");

        InputProcessor zipAlignIt = new InputProcessor() {
            @Override
            public void process(final String split, final File file) {
                final File out = new File(getOutputDirectory(),
                        archivesBaseName + "-" + outputBaseName + "_" + split + ".apk");
                getProject().exec(new Action<ExecSpec>() {
                    @Override
                    public void execute(ExecSpec execSpec) {
                        execSpec.setExecutable(getZipAlignExe());
                        execSpec.args("-f", "4");
                        execSpec.args(file.getAbsolutePath());
                        execSpec.args(out);
                    }
                });
            }
        };
        forEachUnalignedInput(zipAlignIt);
        forEachUnsignedInput(zipAlignIt);
        saveApkMetadataFile();
    }

    @Override
    public List<FilterData> getSplitsData() {
        ImmutableList.Builder<FilterData> filterDataBuilder = ImmutableList.builder();
        SplitRelatedTask.addAllFilterData(filterDataBuilder, densityFilters, FilterType.DENSITY);
        SplitRelatedTask.addAllFilterData(filterDataBuilder, languageFilters, FilterType.LANGUAGE);
        SplitRelatedTask.addAllFilterData(filterDataBuilder, abiFilters, FilterType.ABI);
        return filterDataBuilder.build();
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<SplitZipAlign> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("zipAlign", "SplitPackages");
        }

        @Override
        @NonNull
        public Class<SplitZipAlign> getType() {
            return SplitZipAlign.class;
        }

        @Override
        public void execute(@NonNull SplitZipAlign zipAlign) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            List<? extends BaseVariantOutputData> outputs = variantData.getOutputs();
            final BaseVariantOutputData variantOutputData = outputs.get(0);

            final VariantConfiguration config = scope.getVariantConfiguration();
            Set<String> densityFilters = variantData.getFilters(DENSITY);
            Set<String> abiFilters = variantData.getFilters(ABI);
            Set<String> languageFilters = variantData.getFilters(LANGUAGE);

            zipAlign.setVariantName(config.getFullName());
            ConventionMappingHelper.map(zipAlign, "zipAlignExe", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    final TargetInfo info =
                            scope.getGlobalScope().getAndroidBuilder().getTargetInfo();
                    if (info == null) {
                        return null;
                    }
                    String path = info.getBuildTools().getPath(ZIP_ALIGN);
                    if (path == null) {
                        return null;
                    }
                    return new File(path);
                }
            });

            zipAlign.setOutputDirectory(new File(scope.getGlobalScope().getBuildDir(), "outputs/apk"));
            ConventionMappingHelper.map(zipAlign, "densityOrLanguageInputFiles",
                    new Callable<List<File>>() {
                        @Override
                        public List<File> call() {
                            return variantOutputData.packageSplitResourcesTask.getOutputFiles();
                        }
                    });
            zipAlign.setOutputBaseName(config.getBaseName());
            zipAlign.setAbiFilters(abiFilters);
            zipAlign.setLanguageFilters(languageFilters);
            zipAlign.setDensityFilters(densityFilters);
            File metadataDirectory = new File(zipAlign.getOutputDirectory().getParentFile(),
                    "metadata");
            zipAlign.setApkMetadataFile(new File(metadataDirectory, config.getFullName() + ".mtd"));
            ((ApkVariantOutputData) variantOutputData).splitZipAlign = zipAlign;

            zipAlign.useOldPackaging = AndroidGradleOptions.useOldPackaging(
                    scope.getGlobalScope().getProject());
        }
    }
}
