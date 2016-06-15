/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.aapt.AaptGradleFactory;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@ParallelizableTask
public class ProcessAndroidResources extends IncrementalTask {

    private File manifestFile;

    private File instantRunManifestFile;

    private File resDir;

    private File assetsDir;

    private File sourceOutputDir;

    private File textSymbolOutputDir;

    private File packageOutputFile;

    private File proguardOutputFile;

    private File mainDexListProguardOutputFile;

    private Collection<String> resourceConfigs;

    private String preferredDensity;

    private List<AndroidLibrary> libraries;

    private String packageForR;

    private Collection<String> splits;

    private boolean enforceUniquePackageName;

    private VariantType type;

    private boolean debuggable;

    private boolean pseudoLocalesEnabled;

    private AaptOptions aaptOptions;

    private File mergeBlameLogFolder;

    private InstantRunBuildContext instantRunBuildContext;

    private File instantRunSupportDir;

    private File buildInfoFile;

    private VariantScope variantScope;

    @Override
    protected void doFullTaskAction() throws IOException {
        // we have to clean the source folder output in case the package name changed.
        File srcOut = getSourceOutputDir();
        if (srcOut != null) {
            FileUtils.cleanOutputDir(srcOut);
        }

        @Nullable
        File resOutBaseNameFile = getPackageOutputFile();

        // If are in instant run mode and we have an instant run enabled manifest
        File instantRunManifest = getInstantRunManifestFile();
        File manifestFileToPackage = instantRunBuildContext.isInInstantRunMode() &&
                instantRunManifest != null && instantRunManifest.exists()
                    ? instantRunManifest
                    : getManifestFile();

        AndroidBuilder builder = getBuilder();
        MergingLog mergingLog = new MergingLog(getMergeBlameLogFolder());
        ProcessOutputHandler processOutputHandler = new ParsingProcessOutputHandler(
                new ToolOutputParser(new AaptOutputParser(), getILogger()),
                new MergingLogRewriter(mergingLog, builder.getErrorReporter()));

        try {
            Aapt aapt = AaptGradleFactory.make(
                    getBuilder(),
                    processOutputHandler,
                    true,
                    true,
                    variantScope.getGlobalScope().getProject(),
                    variantScope.getVariantConfiguration().getType(),
                    FileUtils.mkdirs(new File(getIncrementalFolder(), "aapt-temp")));

            AaptPackageConfig.Builder config = new AaptPackageConfig.Builder()
                    .setManifestFile(manifestFileToPackage)
                    .setOptions(getAaptOptions())
                    .setResourceDir(getResDir())
                    .setLibraries(getLibraries())
                    .setCustomPackageForR(getPackageForR())
                    .setSymbolOutputDir(getTextSymbolOutputDir())
                    .setSourceOutputDir(srcOut)
                    .setResourceOutputApk(resOutBaseNameFile)
                    .setProguardOutputFile(getProguardOutputFile())
                    .setMainDexListProguardOutputFile(getMainDexListProguardOutputFile())
                    .setVariantType(getType())
                    .setDebuggable(getDebuggable())
                    .setPseudoLocalize(getPseudoLocalesEnabled())
                    .setResourceConfigs(getResourceConfigs())
                    .setSplits(getSplits())
                    .setPreferredDensity(getPreferredDensity());

            builder.processResources(aapt, config, getEnforceUniquePackageName());

            if (resOutBaseNameFile != null) {
                if (instantRunBuildContext.isInInstantRunMode()) {

                    instantRunBuildContext.addChangedFile(
                            InstantRunBuildContext.FileType.RESOURCES, resOutBaseNameFile);

                    // get the new manifest file CRC
                    String currentIterationCRC = null;
                    try (JarFile jarFile = new JarFile(resOutBaseNameFile)) {
                        ZipEntry entry = jarFile.getEntry(SdkConstants.ANDROID_MANIFEST_XML);
                        if (entry != null) {
                            currentIterationCRC = String.valueOf(entry.getCrc());
                        }
                    }

                    // check the manifest file binary format.
                    File crcFile = new File(instantRunSupportDir, "manifest.crc");
                    if (crcFile.exists() && currentIterationCRC != null) {
                        // compare its content with the new binary file crc.
                        String previousIterationCRC = Files.readFirstLine(crcFile, Charsets.UTF_8);
                        if (!currentIterationCRC.equals(previousIterationCRC)) {
                            instantRunBuildContext.setVerifierResult(
                                    InstantRunVerifierStatus.BINARY_MANIFEST_FILE_CHANGE);
                        }
                    }

                    // write the new manifest file CRC.
                    Files.createParentDirs(crcFile);
                    Files.write(currentIterationCRC, crcFile, Charsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ProcessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSplitPackage(File file, File resBaseName) {
        if (file.getName().startsWith(resBaseName.getName())) {
            for (String split : splits) {
                if (file.getName().contains(split)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static String absolutePath(@Nullable File file) {
        return file == null ? null : file.getAbsolutePath();
    }

    public static class ConfigAction implements TaskConfigAction<ProcessAndroidResources> {

        private final VariantOutputScope scope;
        private final File symbolLocation;
        private final boolean generateResourcePackage;
        private final boolean generateLegacyMultidexMainDexProguardRules;

        public ConfigAction(
                @NonNull VariantOutputScope scope,
                @NonNull File symbolLocation,
                boolean generateResourcePackage,
                boolean generateLegacyMultidexMainDexProguardRules) {
            this.scope = scope;
            this.symbolLocation = symbolLocation;
            this.generateResourcePackage = generateResourcePackage;
            this.generateLegacyMultidexMainDexProguardRules = generateLegacyMultidexMainDexProguardRules;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("process", "Resources");
        }

        @NonNull
        @Override
        public Class<ProcessAndroidResources> getType() {
            return ProcessAndroidResources.class;
        }

        @Override
        public void execute(@NonNull ProcessAndroidResources processResources) {
            final BaseVariantOutputData variantOutputData = scope.getVariantOutputData();
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantScope().getVariantData();
            variantOutputData.processResourcesTask = processResources;
            final GradleVariantConfiguration config = variantData.getVariantConfiguration();

            processResources.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            processResources.setVariantName(config.getFullName());
            processResources.variantScope = scope.getVariantScope();
            processResources.setIncrementalFolder(
                    scope.getVariantScope().getIncrementalDir(getName()));

            if (variantData.getSplitHandlingPolicy() ==
                    SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY) {
                Set<String> allFilters = new HashSet<>();
                allFilters.addAll(
                        variantData.getFilters(com.android.build.OutputFile.FilterType.DENSITY));
                allFilters.addAll(
                        variantData.getFilters(com.android.build.OutputFile.FilterType.LANGUAGE));
                processResources.splits = allFilters;
            }

            // only generate code if the density filter is null, and if we haven't generated
            // it yet (if you have abi + density splits, then several abi output will have no
            // densityFilter)
            if (variantOutputData.getMainOutputFile()
                    .getFilter(com.android.build.OutputFile.DENSITY) == null
                    && variantData.generateRClassTask == null) {
                variantData.generateRClassTask = processResources;
                processResources.enforceUniquePackageName = scope.getGlobalScope().getExtension()
                        .getEnforceUniquePackageName();

                ConventionMappingHelper.map(processResources, "libraries",
                        new Callable<List<AndroidLibrary>>() {
                            @Override
                            public List<AndroidLibrary> call() throws Exception {
                                return config.getFlatPackageAndroidLibraries();
                            }
                        });
                ConventionMappingHelper.map(processResources, "packageForR",
                        new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return config.getOriginalApplicationId();
                            }
                        });

                // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
                processResources
                        .setSourceOutputDir(scope.getVariantScope().getRClassSourceOutputDir());
                processResources.setTextSymbolOutputDir(symbolLocation);

                if (config.getBuildType().isMinifyEnabled()) {
                    if (config.getBuildType().isShrinkResources() && config.getJackOptions().isEnabled()) {
                        LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                                scope.getGlobalScope().getProject(),
                                "shrinkResources does not yet work with useJack=true");
                    }
                    processResources.setProguardOutputFile(
                            scope.getVariantScope().getProcessAndroidResourcesProguardOutputFile());

                } else if (config.getBuildType().isShrinkResources()) {
                    LoggingUtil.displayWarning(Logging.getLogger(getClass()),
                            scope.getGlobalScope().getProject(),
                            "To shrink resources you must also enable ProGuard");
                }

                if (generateLegacyMultidexMainDexProguardRules) {
                    processResources.setAaptMainDexListProguardOutputFile(
                            scope.getVariantScope().getManifestKeepListProguardFile());
                }
            }

            ConventionMappingHelper.map(processResources, "manifestFile", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return variantOutputData.manifestProcessorTask.getOutputFile();
                }
            });

            ConventionMappingHelper.map(processResources, "instantRunManifestFile",
                    new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return variantOutputData.manifestProcessorTask.getInstantRunManifestOutputFile();
                }
            });

            ConventionMappingHelper.map(processResources, "resDir", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getVariantScope().getFinalResourcesDir();
                }
            });

            ConventionMappingHelper.map(processResources, "assetsDir", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return variantData.mergeAssetsTask.getOutputDir();
                }
            });

            if (generateResourcePackage) {
                processResources.setPackageOutputFile(scope.getProcessResourcePackageOutputFile());
            }

            processResources.setType(config.getType());
            processResources.setDebuggable(config.getBuildType().isDebuggable());
            processResources.setAaptOptions(scope.getGlobalScope().getExtension().getAaptOptions());
            processResources
                    .setPseudoLocalesEnabled(config.getBuildType().isPseudoLocalesEnabled());

            ConventionMappingHelper.map(processResources, "resourceConfigs",
                    new Callable<Collection<String>>() {
                        @Override
                        public Collection<String> call() throws Exception {
                            Collection<String> resConfigs =
                                    config.getMergedFlavor().getResourceConfigurations();
                            if (resConfigs.size() == 1 &&
                                    Iterators.getOnlyElement(resConfigs.iterator())
                                            .equals("auto")) {
                                if (scope.getGlobalScope().getAndroidBuilder().getTargetInfo()
                                        .getBuildTools().getRevision().getMajor() >= 21) {
                                    return variantData.discoverListOfResourceConfigsNotDensities();
                                } else {
                                    return variantData.discoverListOfResourceConfigs();
                                }
                            }
                            return config.getMergedFlavor().getResourceConfigurations();
                        }
                    });

            ConventionMappingHelper.map(processResources, "preferredDensity",
                    new Callable<String>() {
                        @Override
                        @Nullable
                        public String call() throws Exception {
                            String variantFilter = variantOutputData.getMainOutputFile()
                                    .getFilter(com.android.build.OutputFile.DENSITY);
                            if (variantFilter != null) {
                                return variantFilter;
                            }
                            return AndroidGradleOptions.getBuildTargetDensity(
                                    scope.getGlobalScope().getProject());
                        }
                    });


            processResources.setMergeBlameLogFolder(
                    scope.getVariantScope().getResourceBlameLogDir());

            processResources.instantRunSupportDir =
                    scope.getVariantScope().getInstantRunSupportDir();

            processResources.instantRunBuildContext =
                    scope.getVariantScope().getInstantRunBuildContext();
            processResources.buildInfoFile =
                    InstantRunWrapperTask.ConfigAction.getTmpBuildInfoFile(scope.getVariantScope());
        }
    }

    @InputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    /**
     * Not an input, it's optional and should never change independently of the main manifest file.
     * <p>
     * The change is handled by {@link #isInstantRunMode()}.
     */
    public File getInstantRunManifestFile() {
        return instantRunManifestFile;
    }

    public void setInstantRunManifestFile(File manifestFile) {
        this.instantRunManifestFile = manifestFile;
    }

    /**
     * To force the task to execute when the manifest file to use changes.
     * <p>
     * Fix for <a href="http://b.android.com/209985">b.android.com/209985</a>.
     */
    @SuppressWarnings("unused")
    @Input
    public boolean isInstantRunMode() {
        return this.instantRunBuildContext.isInInstantRunMode();
    }

    @NonNull
    @InputDirectory
    public File getResDir() {
        return resDir;
    }

    public void setResDir(@NonNull File resDir) {
        this.resDir = resDir;
    }

    @InputDirectory
    @Optional
    @Nullable
    public File getAssetsDir() {
        return assetsDir;
    }

    public void setAssetsDir(File assetsDir) {
        this.assetsDir = assetsDir;
    }

    @OutputDirectory
    @Optional
    @Nullable
    public File getSourceOutputDir() {
        return sourceOutputDir;
    }

    public void setSourceOutputDir(File sourceOutputDir) {
        this.sourceOutputDir = sourceOutputDir;
    }

    @OutputDirectory
    @Optional
    @Nullable
    public File getTextSymbolOutputDir() {
        return textSymbolOutputDir;
    }

    public void setTextSymbolOutputDir(File textSymbolOutputDir) {
        this.textSymbolOutputDir = textSymbolOutputDir;
    }

    @OutputFile
    @Optional
    @Nullable
    public File getPackageOutputFile() {
        return packageOutputFile;
    }

    public void setPackageOutputFile(File packageOutputFile) {
        this.packageOutputFile = packageOutputFile;
    }

    @OutputFile
    @Optional
    @Nullable
    public File getProguardOutputFile() {
        return proguardOutputFile;
    }

    public void setProguardOutputFile(File proguardOutputFile) {
        this.proguardOutputFile = proguardOutputFile;
    }

    @OutputFile
    @Optional
    @Nullable
    public File getMainDexListProguardOutputFile() {
        return mainDexListProguardOutputFile;
    }

    public void setAaptMainDexListProguardOutputFile(File mainDexListProguardOutputFile) {
        this.mainDexListProguardOutputFile = mainDexListProguardOutputFile;
    }

    @Input
    public Collection<String> getResourceConfigs() {
        return resourceConfigs;
    }

    public void setResourceConfigs(Collection<String> resourceConfigs) {
        this.resourceConfigs = resourceConfigs;
    }

    @Input
    @Optional
    @Nullable
    public String getPreferredDensity() {
        return preferredDensity;
    }

    public void setPreferredDensity(String preferredDensity) {
        this.preferredDensity = preferredDensity;
    }

    @Input
    String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @InputFiles
    @Optional
    @Nullable
    public List<File> getInputFilesFromLibraries() {
        List<AndroidLibrary> libs = getLibraries();
        if (libs == null) {
            return ImmutableList.of();
        }
        List<File> files = Lists.newArrayListWithCapacity(libs.size() * 2);
        for (AndroidLibrary androidLibrary : libs) {
            files.add(androidLibrary.getManifest());
            files.add(androidLibrary.getSymbolFile());
        }
        return files;
    }

    public List<AndroidLibrary> getLibraries() {
        return libraries;
    }

    @Input
    @Optional
    @Nullable
    public String getPackageForR() {
        return packageForR;
    }

    public void setPackageForR(String packageForR) {
        this.packageForR = packageForR;
    }

    @Input
    @Optional
    @Nullable
    public Collection<String> getSplits() {
        return splits;
    }

    public void setSplits(Collection<String> splits) {
        this.splits = splits;
    }

    @Input
    public boolean getEnforceUniquePackageName() {
        return enforceUniquePackageName;
    }

    public void setEnforceUniquePackageName(boolean enforceUniquePackageName) {
        this.enforceUniquePackageName = enforceUniquePackageName;
    }

    /** Does not change between incremental builds, so does not need to be @Input. */
    public VariantType getType() {
        return type;
    }

    public void setType(VariantType type) {
        this.type = type;
    }

    @Input
    public boolean getDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Input
    public boolean getPseudoLocalesEnabled() {
        return pseudoLocalesEnabled;
    }

    public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
        this.pseudoLocalesEnabled = pseudoLocalesEnabled;
    }

    @Nested
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    public void setAaptOptions(AaptOptions aaptOptions) {
        this.aaptOptions = aaptOptions;
    }

    @Input
    public File getMergeBlameLogFolder() {
        return mergeBlameLogFolder;
    }

    public void setMergeBlameLogFolder(File mergeBlameLogFolder) {
        this.mergeBlameLogFolder = mergeBlameLogFolder;
    }
}
