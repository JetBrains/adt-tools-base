package com.android.build.gradle.tasks;

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

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;

@ParallelizableTask
public class PackageApplication extends IncrementalTask implements FileSupplier {

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
            Collection<File> javaResourceFiles = getJavaResourceFiles();
            getBuilder().packageApk(
                    getResourceFile().getAbsolutePath(),
                    getDexFolders(),
                    javaResourceFiles == null ? ImmutableList.<File>of() : javaResourceFiles,
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
        private final boolean addDexFilesToApk;

        public ConfigAction(VariantOutputScope scope, boolean addDexFilesToApk) {
            this.scope = scope;
            this.addDexFilesToApk = addDexFilesToApk;
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

                    return addDexFilesToApk
                        ? variantScope.getTransformManager()
                            .getPipelineOutput(sDexFilter).keySet()
                        : ImmutableSet.<File>of();
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

        private static File getOptionalDir(File dir) {
            if (dir.isDirectory()) {
                return dir;
            }

            return null;
        }
    }
}
