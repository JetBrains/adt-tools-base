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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.api.ApkOutputFile;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.PackagingUtils;
import com.android.builder.packaging.SigningException;
import com.android.ide.common.signing.KeytoolException;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Package a abi dimension specific split APK
 */
@ParallelizableTask
public class PackageSplitAbi extends SplitRelatedTask {

    private ImmutableList<ApkOutputFile> outputFiles;

    private Collection<File> inputFiles;

    private File outputDirectory;

    private Set<String> splits;

    private String outputBaseName;

    private boolean jniDebuggable;

    private SigningConfig signingConfig;

    private Collection<File> jniFolders;

    private ApiVersion minSdkVersion;

    private File incrementalDir;

    private File manifest;

    private AaptOptions aaptOptions;

    @OutputFiles
    public List<File> getOutputFiles() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (String split : splits) {
            String apkName = getApkName(split);
            builder.add(new File(outputDirectory, apkName));
        }
        return builder.build();
    }

    @Override
    @Nullable
    public File getApkMetadataFile() {
        return null;
    }

    @Override
    @NonNull
    public synchronized ImmutableList<ApkOutputFile> getOutputSplitFiles() {

        if (outputFiles == null) {
            ImmutableList.Builder<ApkOutputFile> builder = ImmutableList.builder();
            for (String split : splits) {
                String apkName = getApkName(split);
                ApkOutputFile apkOutput = new ApkOutputFile(
                        OutputFile.OutputType.SPLIT,
                        ImmutableList.of(FilterDataImpl.build(OutputFile.ABI, apkName)),
                        Callables.returning(new File(outputDirectory, apkName)));
                builder.add(apkOutput);
            }

            outputFiles = builder.build();
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
    protected void doFullTaskAction() throws FileNotFoundException, SigningException,
            KeytoolException, PackagerException, IOException {

        // resources- and .ap_ should be shared in a setting somewhere. see BasePlugin:1206
        final Pattern pattern = Pattern.compile("resources-" + getOutputBaseName() + "-(.*).ap_");
        List<String> unprocessedSplits = Lists.newArrayList(splits);
        for (File file : inputFiles) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches() && isAbiSplit(file.getName())) {
                String apkName = getApkName(matcher.group(1));

                File outFile = new File(getOutputDirectory(), apkName);
                getBuilder().oldPackageApk(
                        file.getAbsolutePath(),
                        ImmutableSet.of(), /* dexFolders */
                        ImmutableList.of(), /* getJavaResourceDir */
                        getJniFolders(),
                        null,
                        ImmutableSet.of(matcher.group(1)),
                        isJniDebuggable(),
                        getSigningConfig(),
                        outFile,
                        getMinSdkVersion(),
                        PackagingUtils.getNoCompressPredicate(aaptOptions, manifest));
                unprocessedSplits.remove(matcher.group(1));
            }
        }
        if (!unprocessedSplits.isEmpty()) {
            String message = "Could not find resource package for "
                    + Joiner.on(',').join(unprocessedSplits);
            getLogger().error(message);
            throw new IllegalStateException(message);
        }
    }

    @Override
    public List<FilterData> getSplitsData() {
        ImmutableList.Builder<FilterData> filterDataBuilder = ImmutableList.builder();
        SplitRelatedTask.addAllFilterData(filterDataBuilder, splits, OutputFile.FilterType.ABI);
        return filterDataBuilder.build();
    }

    private String getApkName(final String split) {
        String archivesBaseName = (String)getProject().getProperties().get("archivesBaseName");
        String apkName = archivesBaseName + "-" + getOutputBaseName() + "_" + split;
        return apkName + (getSigningConfig() == null ? "-unsigned.apk" : "-unaligned.apk");
    }

    public void setOutputFiles(ImmutableList<ApkOutputFile> outputFiles) {
        this.outputFiles = outputFiles;
    }

    @InputFiles
    public Collection<File> getInputFiles() {
        return inputFiles;
    }

    public void setInputFiles(Collection<File> inputFiles) {
        this.inputFiles = inputFiles;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Input
    public Set<String> getSplits() {
        return splits;
    }

    public void setSplits(Set<String> splits) {
        this.splits = splits;
    }

    @Input
    public String getOutputBaseName() {
        return outputBaseName;
    }

    public void setOutputBaseName(String outputBaseName) {
        this.outputBaseName = outputBaseName;
    }

    @Input
    public boolean isJniDebuggable() {
        return jniDebuggable;
    }

    public void setJniDebuggable(boolean jniDebuggable) {
        this.jniDebuggable = jniDebuggable;
    }

    @Nested
    @Optional
    public SigningConfig getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(SigningConfig signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Input
    public Collection<File> getJniFolders() {
        return jniFolders;
    }

    public void setJniFolders(Collection<File> jniFolders) {
        this.jniFolders = jniFolders;
    }

    public void setMinSdkVersion(ApiVersion version) {
        this.minSdkVersion = version;
    }

    @Input
    public int getMinSdkVersion() {
        return minSdkVersion.getApiLevel();
    }

    @Input
    public Collection<String> getNoCompressExtensions() {
        return MoreObjects.firstNonNull(aaptOptions.getNoCompress(), Collections.emptyList());
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<PackageSplitAbi> {

        private VariantScope scope;

        public ConfigAction(VariantScope scope) {
            this.scope = scope;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName("package", "SplitAbi");
        }

        @Override
        @NonNull
        public Class<PackageSplitAbi> getType() {
            return PackageSplitAbi.class;
        }

        @Override
        public void execute(@NonNull PackageSplitAbi packageSplitAbiTask) {
            BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            List<? extends BaseVariantOutputData> outputs = variantData.getOutputs();
            BaseVariantOutputData variantOutputData = outputs.get(0);
            VariantOutputScope variantOutputScope = variantOutputData.getScope();
            VariantConfiguration config = this.scope.getVariantConfiguration();

            variantOutputData.packageSplitAbiTask = packageSplitAbiTask;

            Set<String> filters = AbiSplitOptions.getAbiFilters(
                    this.scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
            packageSplitAbiTask.setInputFiles(this.scope.getSplitAbiResOutputFiles());
            packageSplitAbiTask.setSplits(filters);
            packageSplitAbiTask.setOutputBaseName(config.getBaseName());
            packageSplitAbiTask.setSigningConfig(config.getSigningConfig());
            packageSplitAbiTask.setOutputDirectory(this.scope.getSplitOutputDirectory());
            packageSplitAbiTask.setAndroidBuilder(this.scope.getGlobalScope().getAndroidBuilder());
            packageSplitAbiTask.setVariantName(config.getFullName());
            packageSplitAbiTask.setMinSdkVersion(config.getMinSdkVersion());
            packageSplitAbiTask.incrementalDir = this.scope.getIncrementalDir(
                    packageSplitAbiTask.getName());

            packageSplitAbiTask.aaptOptions =
                    scope.getGlobalScope().getExtension().getAaptOptions();
            packageSplitAbiTask.manifest = variantOutputScope.getManifestOutputFile();

            ConventionMappingHelper.map(
                    packageSplitAbiTask,
                    "jniFolders",
                    () -> this.scope.getTransformManager()
                            .getPipelineOutput(StreamFilter.NATIVE_LIBS)
                            .keySet());

            ConventionMappingHelper.map(
                    packageSplitAbiTask,
                    "jniDebuggable",
                    () -> config.getBuildType().isJniDebuggable());
        }
    }
}
