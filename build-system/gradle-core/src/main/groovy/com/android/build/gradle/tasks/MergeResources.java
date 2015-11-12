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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.png.QueuedCruncher;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.res2.FileValidity;
import com.android.ide.common.res2.GeneratedResourceSet;
import com.android.ide.common.res2.MergedResourceWriter;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.NoOpResourcePreprocessor;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourcePreprocessor;
import com.android.ide.common.res2.ResourceSet;
import com.android.resources.Density;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@ParallelizableTask
public class MergeResources extends IncrementalTask {

    private static final List<Pattern> IGNORED_WARNINGS = Lists.newArrayList(
            Pattern.compile("Not recognizing known sRGB profile that has been edited"));

    // ----- PUBLIC TASK API -----

    /**
     * Directory to write the merged resources to
     */
    private File outputDir;

    private File generatedPngsOutputDir;

    // ----- PRIVATE TASK API -----

    /**
     * Optional file to write any publicly imported resource types and names to
     */
    private File publicFile;

    private boolean process9Patch;

    private boolean crunchPng;

    private boolean useNewCruncher;

    private boolean validateEnabled;

    private File blameLogFolder;
    // actual inputs
    private List<ResourceSet> inputResourceSets;

    private final FileValidity<ResourceSet> fileValidity = new FileValidity<ResourceSet>();

    private Collection<String> generatedDensities;

    private int minSdk;

    @InputFiles
    @SuppressWarnings("unused") // Fake input to detect changes. Not actually used by the task.
    public Iterable<File> getRawInputFolders() {
        return flattenSourceSets(getInputResourceSets());
    }

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    @Override
    protected boolean isIncremental() {
        return true;
    }

    private PngCruncher getCruncher() {
        if (getUseNewCruncher()) {
            // At this point ensureTargetSetup() has been called, so no NPE below.
            // noinspection ConstantConditions
            BuildToolInfo buildTools = getBuilder().getTargetInfo().getBuildTools();
            if (buildTools.getRevision().getMajor() >= 22) {
                return QueuedCruncher.Builder.INSTANCE.newCruncher(
                        buildTools.getPath(BuildToolInfo.PathId.AAPT),
                        getFilteringLogger());
            }
            getLogger().info("New PNG cruncher will be enabled with build tools 22 and above.");
        }

        return getBuilder().getAaptCruncher(new LoggedProcessOutputHandler(getFilteringLogger()));
    }

    /**
     * Returns an {@link ILogger} that degrades certain warnings to INFO level.
     */
    @NonNull
    private ILogger getFilteringLogger() {
        final ILogger delegate = getILogger();

        return new ILogger() {
            @Override
            public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
                if (msgFormat != null && isIgnored(msgFormat, args)) {
                    delegate.info(Strings.nullToEmpty(msgFormat), args);
                } else {
                    delegate.error(t, msgFormat, args);
                }
            }

            @Override
            public void warning(@NonNull String msgFormat, Object... args) {
                if (isIgnored(msgFormat, args)) {
                    delegate.info(msgFormat, args);
                } else {
                    delegate.warning(msgFormat, args);
                }
            }

            @Override
            public void info(@NonNull String msgFormat, Object... args) {
                delegate.info(msgFormat, args);
            }

            @Override
            public void verbose(@NonNull String msgFormat, Object... args) {
                delegate.verbose(msgFormat, args);
            }

            private boolean isIgnored(String msgFormat, Object... args) {
                String message = String.format(msgFormat, args);
                for (Pattern pattern : IGNORED_WARNINGS) {
                    if (pattern.matcher(message).find()) {
                        return true;
                    }
                }

                return false;
            }
        };
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        ResourcePreprocessor preprocessor = getPreprocessor();

        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.emptyFolder(destinationDir);

        List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

        // create a new merger and populate it with the sets.
        ResourceMerger merger = new ResourceMerger(minSdk);

        try {
            for (ResourceSet resourceSet : resourceSets) {
                resourceSet.loadFromFiles(getILogger());
                merger.addDataSet(resourceSet);
            }

            // get the merged set and write it down.
            MergedResourceWriter writer = new MergedResourceWriter(
                    destinationDir,
                    getCruncher(),
                    getCrunchPng(),
                    getProcess9Patch(),
                    getPublicFile(),
                    getBlameLogFolder(),
                    preprocessor);

            merger.mergeData(writer, false /*doCleanUp*/);

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer);
        } catch (MergingException e) {
            System.out.println(e.getMessage());
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        ResourcePreprocessor preprocessor = getPreprocessor();

        // create a merger and load the known state.
        ResourceMerger merger = new ResourceMerger(minSdk);
        try {
            if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
                doFullTaskAction();
                return;
            }

            for (ResourceSet resourceSet : merger.getDataSets()) {
                resourceSet.setPreprocessor(preprocessor);
            }

            List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                getLogger().info("Changed Resource sets: full task run!");
                doFullTaskAction();
                return;
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (Map.Entry<File, FileStatus> entry : changedInputs.entrySet()) {
                File changedFile = entry.getKey();

                merger.findDataSetContaining(changedFile, fileValidity);
                if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                    doFullTaskAction();
                    return;
                } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                    if (!fileValidity.getDataSet().updateWith(
                            fileValidity.getSourceFile(), changedFile, entry.getValue(),
                            getILogger())) {
                        getLogger().info(
                                String.format("Failed to process %s event! Full task run",
                                        entry.getValue()));
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergedResourceWriter writer = new MergedResourceWriter(
                    getOutputDir(),
                    getCruncher(),
                    getCrunchPng(),
                    getProcess9Patch(),
                    getPublicFile(),
                    getBlameLogFolder(),
                    preprocessor);
            merger.mergeData(writer, false /*doCleanUp*/);
            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer);
        } catch (MergingException e) {
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(e.getMessage(), e);
        } finally {
            // some clean up after the task to help multi variant/module builds.
            fileValidity.clear();
        }
    }

    @Nullable
    private ResourcePreprocessor getPreprocessor() {
        // Only one pre-processor for now. The code will need slight changes when we add more.
        Collection<String> generatedDensitiesNames = getGeneratedDensities();

        if (generatedDensitiesNames.isEmpty()) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return new NoOpResourcePreprocessor();
        }

        Collection<Density> densities = Lists.newArrayList();
        for (String density : generatedDensitiesNames) {
            densities.add(Density.getEnum(density));
        }

        return new VectorDrawableRenderer(
                getMinSdk(),
                getGeneratedPngsOutputDir(),
                densities,
                getILogger());
    }

    @NonNull
    private List<ResourceSet> getConfiguredResourceSets(ResourcePreprocessor preprocessor) {
        List<ResourceSet> resourceSets = Lists.newArrayList(getInputResourceSets());
        List<ResourceSet> generatedSets = Lists.newArrayListWithCapacity(resourceSets.size());

        for (ResourceSet resourceSet : resourceSets) {
            resourceSet.setPreprocessor(preprocessor);
            ResourceSet generatedSet = new GeneratedResourceSet(resourceSet);
            resourceSet.setGeneratedSet(generatedSet);
            generatedSets.add(generatedSet);
        }

        // Put all generated sets at the start of the list.
        resourceSets.addAll(0, generatedSets);
        return resourceSets;
    }

    public List<ResourceSet> getInputResourceSets() {
        return inputResourceSets;
    }

    @SuppressWarnings("unused") // Property set with convention mapping.
    public void setInputResourceSets(
            List<ResourceSet> inputResourceSets) {
        this.inputResourceSets = inputResourceSets;
    }

    public boolean getUseNewCruncher() {
        return useNewCruncher;
    }

    public void setUseNewCruncher(boolean useNewCruncher) {
        this.useNewCruncher = useNewCruncher;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    public boolean getCrunchPng() {
        return crunchPng;
    }

    public void setCrunchPng(boolean crunchPng) {
        this.crunchPng = crunchPng;
    }

    public boolean getProcess9Patch() {
        return process9Patch;
    }

    public void setProcess9Patch(boolean process9Patch) {
        this.process9Patch = process9Patch;
    }

    @Optional
    @OutputFile
    public File getPublicFile() {
        return publicFile;
    }

    public void setPublicFile(File publicFile) {
        this.publicFile = publicFile;
    }

    // Synthetic input: the validation flag is set on the resource sets in ConfigAction.execute.
    @SuppressWarnings("unused")
    @Input
    public boolean isValidateEnabled() {
        return validateEnabled;
    }

    public void setValidateEnabled(boolean validateEnabled) {
        this.validateEnabled = validateEnabled;
    }

    @OutputDirectory
    @Optional
    public File getBlameLogFolder() {
        return blameLogFolder;
    }

    public void setBlameLogFolder(File blameLogFolder) {
        this.blameLogFolder = blameLogFolder;
    }

    public File getGeneratedPngsOutputDir() {
        return generatedPngsOutputDir;
    }

    public void setGeneratedPngsOutputDir(File generatedPngsOutputDir) {
        this.generatedPngsOutputDir = generatedPngsOutputDir;
    }

    @Input
    public Collection<String> getGeneratedDensities() {
        return generatedDensities;
    }

    @Input
    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public void setGeneratedDensities(Collection<String> generatedDensities) {
        this.generatedDensities = generatedDensities;
    }

    public static class ConfigAction implements TaskConfigAction<MergeResources> {

        @NonNull
        private final VariantScope scope;

        @NonNull
        private final String taskNamePrefix;

        @Nullable
        private final File outputLocation;

        private final boolean includeDependencies;

        private final boolean process9Patch;

        public ConfigAction(
                @NonNull VariantScope scope,
                @NonNull String taskNamePrefix,
                @Nullable File outputLocation,
                boolean includeDependencies,
                boolean process9Patch) {
            this.scope = scope;
            this.taskNamePrefix = taskNamePrefix;
            this.outputLocation = outputLocation;
            this.includeDependencies = includeDependencies;
            this.process9Patch = process9Patch;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName(taskNamePrefix, "Resources");
        }

        @NonNull
        @Override
        public Class<MergeResources> getType() {
            return MergeResources.class;
        }

        @Override
        public void execute(@NonNull MergeResources mergeResourcesTask) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData =
                    scope.getVariantData();
            final AndroidConfig extension = scope.getGlobalScope().getExtension();

            mergeResourcesTask.setMinSdk(
                    variantData.getVariantConfiguration().getMinSdkVersion().getApiLevel());

            mergeResourcesTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            mergeResourcesTask.setVariantName(scope.getVariantConfiguration().getFullName());
            mergeResourcesTask.setIncrementalFolder(scope.getIncrementalDir(getName()));

            // Libraries use this task twice, once for compilation (with dependencies),
            // where blame is useful, and once for packaging where it is not.
            if (includeDependencies) {
                mergeResourcesTask.setBlameLogFolder(scope.getResourceBlameLogDir());
            }
            mergeResourcesTask.setProcess9Patch(process9Patch);
            mergeResourcesTask.setCrunchPng(extension.getAaptOptions().getCruncherEnabled());

            Set<String> generatedDensities =
                    variantData.getVariantConfiguration().getMergedFlavor().getGeneratedDensities();
            mergeResourcesTask.setGeneratedDensities(
                    Objects.firstNonNull(generatedDensities, Collections.<String>emptySet()));

            mergeResourcesTask.setUseNewCruncher(extension.getAaptOptions().getUseNewCruncher());

            final boolean validateEnabled = AndroidGradleOptions.isResourceValidationEnabled(
                    scope.getGlobalScope().getProject());

            mergeResourcesTask.setValidateEnabled(validateEnabled);

            ConventionMappingHelper.map(mergeResourcesTask, "inputResourceSets",
                    new Callable<List<ResourceSet>>() {
                        @Override
                        public List<ResourceSet> call() throws Exception {
                            List<File> generatedResFolders = Lists.newArrayList(
                                    scope.getRenderscriptResOutputDir(),
                                    scope.getGeneratedResOutputDir());
                            if (variantData.getExtraGeneratedResFolders() != null) {
                                generatedResFolders.addAll(
                                        variantData.getExtraGeneratedResFolders());
                            }
                            if (variantData.generateApkDataTask != null &&
                                    variantData.getVariantConfiguration().getBuildType()
                                            .isEmbedMicroApp()) {
                                generatedResFolders.add(
                                        variantData.generateApkDataTask.getResOutputDir());
                            }

                            return variantData.getVariantConfiguration().getResourceSets(
                                    generatedResFolders, includeDependencies, validateEnabled);
                        }
                    });

            mergeResourcesTask.setOutputDir(
                    outputLocation != null
                            ? outputLocation
                            : scope.getDefaultMergeResourcesOutputDir());

            mergeResourcesTask.setGeneratedPngsOutputDir(scope.getGeneratedPngsOutputDir());

            variantData.mergeResourcesTask = mergeResourcesTask;
        }
    }
}
