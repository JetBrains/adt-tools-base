package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.annotations.ApkFile;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.FilterableStreamCollection;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.DuplicateFileException;
import com.android.utils.StringHelper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@ParallelizableTask
public class PackageApplication extends IncrementalTask implements FileSupplier {

    public enum DexPackagingPolicy {
        /**
         * Standard Dex packaging policy, all dex files will be packaged at the root of the APK.
         */
        STANDARD,

        /**
         * InstantRun specific Dex packaging policy, all dex files with a name containing
         * {@link InstantRunSlicer#MAIN_SLICE_NAME} will be packaged at the root of the APK while
         * all other dex files will be packaged in a instant-run.zip itself packaged at the root
         * of the APK.
         */
        INSTANT_RUN
    }

    private boolean useOldPackaging;

    public static final FilterableStreamCollection.StreamFilter sDexFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(ExtendedContentType.DEX);
                }
            };

    public static final FilterableStreamCollection.StreamFilter sResFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(QualifiedContent.DefaultContentType.RESOURCES) &&
                            !scopes.contains(Scope.PROVIDED_ONLY) &&
                            !scopes.contains(Scope.TESTED_CODE);
                }
            };

    public static final FilterableStreamCollection.StreamFilter sNativeLibsFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(ExtendedContentType.NATIVE_LIBS) &&
                            !scopes.contains(Scope.PROVIDED_ONLY) &&
                            !scopes.contains(Scope.TESTED_CODE);
                }
            };

    // ----- PUBLIC TASK API -----

    @InputFile
    public File getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    // ----- PRIVATE TASK API -----

    @InputFiles
    @Optional
    public Collection<File> getJavaResourceFiles() {
        return javaResourceFiles;
    }
    @InputFiles
    @Optional
    public Collection<File> getJniFolders() {
        return jniFolders;
    }


    private File resourceFile;

    private Set<File> dexFolders;
    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    private Set<File> javaResourceFiles;
    private Set<File> jniFolders;

    @ApkFile
    private File outputFile;

    private Set<String> abiFilters;

    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private ApiVersion minSdkVersion;

    private InstantRunBuildContext instantRunContext;

    private File instantRunSupportDir;

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public boolean isJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Nested
    @Optional
    public CoreSigningConfig getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(CoreSigningConfig signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Nested
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    public void setPackagingOptions(PackagingOptions packagingOptions) {
        this.packagingOptions = packagingOptions;
    }

    @Input
    public int getMinSdkVersion() {
        return this.minSdkVersion.getApiLevel();
    }

    public void setMinSdkVersion(ApiVersion version) {
        this.minSdkVersion = version;
    }

    @InputFile
    public File getMarkerFile() {
        return markerFile;
    }

    private File markerFile;

    DexPackagingPolicy dexPackagingPolicy;

    @Input
    String getDexPackagingPolicy() {
        return dexPackagingPolicy.toString();
    }

    @Override
    protected void doFullTaskAction() {

        // if the blocker file is there, do not run.
        if (getMarkerFile().exists()) {
            try {
                if (MarkerFile.readMarkerFile(getMarkerFile()) == MarkerFile.Command.BLOCK) {
                    return;
                }
            } catch (IOException e) {
                getLogger().warn("Cannot read marker file, proceed with execution", e);
            }
        }

        try {

            ImmutableSet.Builder<File> dexFoldersForApk = ImmutableSet.builder();
            ImmutableList.Builder<File> javaResourcesForApk = ImmutableList.builder();

            Collection<File> javaResourceFiles = getJavaResourceFiles();
            if (javaResourceFiles != null) {
                javaResourcesForApk.addAll(javaResourceFiles);
            }
            switch(dexPackagingPolicy) {
                case INSTANT_RUN:
                    File zippedDexes = zipDexesForInstantRun(getDexFolders(), dexFoldersForApk);
                    javaResourcesForApk.add(zippedDexes);
                    break;
                case STANDARD:
                    dexFoldersForApk.addAll(getDexFolders());
                    break;
                default:
                    throw new RuntimeException(
                            "Unhandled DexPackagingPolicy : " + getDexPackagingPolicy());
            }

            getBuilder().packageApk(
                    getResourceFile().getAbsolutePath(),
                    dexFoldersForApk.build(),
                    javaResourcesForApk.build(),
                    getJniFolders(),
                    getAbiFilters(),
                    getJniDebugBuild(),
                    getSigningConfig(),
                    getOutputFile().getAbsolutePath(),
                    getMinSdkVersion());
        } catch (DuplicateFileException e) {
            Logger logger = getLogger();
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile()
                    .getAbsolutePath());
            logger.error("\tPath in archive: " + e.getArchivePath());
            int index = 1;
            for (File file : e.getSourceFiles()) {
                logger.error("\tOrigin " + (index++) + ": " + file);
            }
            logger.error("You can ignore those files in your build.gradle:");
            logger.error("\tandroid {");
            logger.error("\t  packagingOptions {");
            logger.error("\t    exclude \'" + e.getArchivePath() + "\'");
            logger.error("\t  }");
            logger.error("\t}");
            throw new BuildException(e.getMessage(), e);
        } catch (Exception e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable rootCause = Throwables.getRootCause(e);
            if (rootCause instanceof NoSuchAlgorithmException) {
                throw new BuildException(
                        rootCause.getMessage() + ": try using a newer JVM to build your application.",
                        rootCause);
            }
            throw new BuildException(e.getMessage(), e);
        }
        // mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overriden if the apk is signed/aligned.
        try {
            instantRunContext.addChangedFile(InstantRunBuildContext.FileType.MAIN,
                    getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    private File zipDexesForInstantRun(Iterable<File> dexFolders,
            ImmutableSet.Builder<File> dexFoldersForApk)
            throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "classes.zip");
        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));

        try {
            for (File dexFolder : dexFolders) {
                if (dexFolder.getName().contains(InstantRunSlicer.MAIN_SLICE_NAME)) {
                    dexFoldersForApk.add(dexFolder);
                } else {
                    for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                        if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                            // There are several pieces of code in the runtime library which depends on
                            // this exact pattern, so it should not be changed without thorough testing
                            // (it's basically part of the contract).
                            String entryName = file.getParentFile().getName() + "-" + file.getName();
                            zipFile.putNextEntry(new ZipEntry(entryName));
                            try {
                                Files.copy(file, zipFile);
                            } finally {
                                zipFile.closeEntry();
                            }
                        }

                    }
                }
            }
        } finally {
            zipFile.close();
        }

        // now package that zip file as a zip since this is what the packager is expecting !
        File finalResourceFile = new File(instantRunSupportDir, "resources.zip");
        zipFile = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(finalResourceFile)));
        try {
            zipFile.putNextEntry(new ZipEntry("instant-run.zip"));
            try {
                Files.copy(tmpZipFile, zipFile);
            } finally {
                zipFile.closeEntry();
            }
        } finally {
            zipFile.close();
        }

        return finalResourceFile;
    }

    // ----- FileSupplierTask -----

    @Override
    public File get() {
        return getOutputFile();
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<PackageApplication> {

        private final VariantOutputScope scope;
        private final DexPackagingPolicy dexPackagingPolicy;

        public ConfigAction(VariantOutputScope scope, DexPackagingPolicy dexPackagingPolicy) {
            this.scope = scope;
            this.dexPackagingPolicy = dexPackagingPolicy;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(@NonNull final PackageApplication packageApp) {
            final VariantScope variantScope = scope.getVariantScope();
            final ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
            final ApkVariantOutputData variantOutputData = (ApkVariantOutputData) scope
                    .getVariantOutputData();
            final GradleVariantConfiguration config = variantScope.getVariantConfiguration();

            variantOutputData.packageApplicationTask = packageApp;
            packageApp.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            packageApp.setVariantName(
                    variantScope.getVariantConfiguration().getFullName());
            packageApp.setMinSdkVersion(config.getMinSdkVersion());
            packageApp.instantRunContext = variantScope.getInstantRunBuildContext();
            packageApp.dexPackagingPolicy = dexPackagingPolicy;
            packageApp.instantRunSupportDir = variantScope.getInstantRunSupportDir();

            if (config.isMinifyEnabled()
                    && config.getBuildType().isShrinkResources()
                    && !config.getUseJack()) {
                ConventionMappingHelper.map(packageApp, "resourceFile", new Callable<File>() {
                    @Override
                    public File call() {
                        return scope.getCompressedResourceFile();
                    }
                });
            } else {
                ConventionMappingHelper.map(packageApp, "resourceFile", new Callable<File>() {
                    @Override
                    public File call() {
                        return variantOutputData.processResourcesTask.getPackageOutputFile();
                    }
                });
            }

            ConventionMappingHelper.map(packageApp, "dexFolders", new Callable<Set<File>>() {
                @Override
                public  Set<File> call() {
                    if (config.getUseJack()) {
                        return ImmutableSet.of(variantScope.getJackDestinationDir());
                    }
                    return variantScope.getTransformManager()
                            .getPipelineOutput(sDexFilter).keySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "javaResourceFiles", new Callable<Set<File>>() {
                @Override
                public Set<File> call() throws Exception {
                    return variantScope.getTransformManager().getPipelineOutput(
                            sResFilter).keySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "jniFolders", new Callable<Set<File>>() {
                @Override
                public Set<File> call() {
                    if (variantData.getSplitHandlingPolicy() ==
                            BaseVariantData.SplitHandlingPolicy.PRE_21_POLICY) {
                        return variantScope.getTransformManager().getPipelineOutput(
                                sNativeLibsFilter).keySet();
                    }

                    Set<String> filters = AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
                    return filters.isEmpty() ? variantScope.getTransformManager().getPipelineOutput(
                            sNativeLibsFilter).keySet() : Collections.<File>emptySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "abiFilters", new Callable<Set<String>>() {
                @Override
                public Set<String> call() throws Exception {
                    if (variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.ABI) != null) {
                        return ImmutableSet.of(
                                variantOutputData.getMainOutputFile()
                                        .getFilter(com.android.build.OutputFile.ABI));
                    }
                    Set<String> supportedAbis = config.getSupportedAbis();
                    if (supportedAbis != null) {
                        return supportedAbis;
                    }

                    return ImmutableSet.of();
                }
            });
            ConventionMappingHelper.map(packageApp, "jniDebugBuild", new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return config.getBuildType().isJniDebuggable();
                }
            });

            CoreSigningConfig sc = (CoreSigningConfig) config.getSigningConfig();
            packageApp.setSigningConfig(sc);
            if (sc != null) {
                String validateSigningTaskName = "validate" + StringHelper.capitalize(sc.getName()) + "Signing";
                ValidateSigningTask validateSigningTask =
                        (ValidateSigningTask) scope.getGlobalScope().getProject().getTasks().findByName(validateSigningTaskName);
                if (validateSigningTask == null) {
                    validateSigningTask =
                            scope.getGlobalScope().getProject().getTasks().create(
                                    "validate" + StringHelper.capitalize(sc.getName()) + "Signing",
                                    ValidateSigningTask.class);
                    validateSigningTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
                    validateSigningTask.setVariantName(
                            variantScope.getVariantConfiguration().getFullName());
                    validateSigningTask.setSigningConfig(sc);
                }

                packageApp.dependsOn(validateSigningTask);
            }

            ConventionMappingHelper.map(packageApp, "packagingOptions", new Callable<PackagingOptions>() {
                @Override
                public PackagingOptions call() throws Exception {
                    return scope.getGlobalScope().getExtension().getPackagingOptions();
                }
            });

            ConventionMappingHelper.map(packageApp, "outputFile", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getPackageApk();
                }
            });

            packageApp.markerFile =
                    PrePackageApplication.ConfigAction.getMarkerFile(variantScope);
            packageApp.useOldPackaging = AndroidGradleOptions.useOldPackaging(
                    variantScope.getGlobalScope().getProject());
        }
    }
}
