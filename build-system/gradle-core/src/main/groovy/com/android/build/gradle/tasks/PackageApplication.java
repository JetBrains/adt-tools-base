package com.android.build.gradle.tasks;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.annotations.ApkFile;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Format;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.signing.SignedJarBuilder;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;

import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@ParallelizableTask
public class PackageApplication extends IncrementalTask implements FileSupplier {

    public static final TransformManager.StreamFilter sDexFilter
            = new TransformManager.StreamFilter() {
        @Override
        public boolean accept(@NonNull Set<ContentType> types,
                @NonNull Set<Scope> scopes) {
            return types.contains(ContentType.DEX);
        }
    };

    public static final TransformManager.StreamFilter sResFilter
            = new TransformManager.StreamFilter() {
        @Override
        public boolean accept(@NonNull Set<ContentType> types,
                @NonNull Set<Scope> scopes) {
            return types.contains(ContentType.RESOURCES) &&
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

    @InputFiles
    public Collection<File> getDexFolderList() {
        return getDexFolders().keySet();
    }

    public Set<File> getJniFolders() {
        return jniFolders;
    }

    public void setJniFolders(Set<File> jniFolders) {
        this.jniFolders = jniFolders;
    }

    public File getMergingFolder() {
        return mergingFolder;
    }

    public void setMergingFolder(File mergingFolder) {
        this.mergingFolder = mergingFolder;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    @Optional
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    // ----- PRIVATE TASK API -----

    @InputDirectory
    @Optional
    public File getJavaResourceDir() {
        return javaResourceDir;
    }

    @InputFile
    @Optional
    public File getJavaResourceJar() {
        return javaResourceJar;
    }

    private File resourceFile;

    private Map<File, Format> dexFolders;
    public Map<File, Format> getDexFolders() {
        return dexFolders;
    }

    /** directory for the merged java resources. only valid if the jar below is null  */
    private File javaResourceDir;
    /** jar for the merged java resources. only valid if the folder above is null  */
    private File javaResourceJar;

    private Set<File> jniFolders;

    private File mergingFolder;

    @ApkFile
    private File outputFile;

    private Set<String> abiFilters;

    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private SignedJarBuilder.IZipEntryFilter packagingOptionsFilter;

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

    public SignedJarBuilder.IZipEntryFilter getPackagingOptionsFilter() {
        return packagingOptionsFilter;
    }

    public void setPackagingOptionsFilter(SignedJarBuilder.IZipEntryFilter filter) {
        this.packagingOptionsFilter = filter;
    }

    @InputFiles
    public FileTree getNativeLibraries() {
        FileTree src = null;
        Set<File> folders = getJniFolders();
        if (!folders.isEmpty()) {
            src = getProject().files(new ArrayList<Object>(folders)).getAsFileTree();
        }

        return src == null ? getProject().files().getAsFileTree() : src;
    }

    @Override
    protected void doFullTaskAction() {
        try {
            File resourceLocation = getJavaResourceDir();
            if (resourceLocation == null) {
                resourceLocation = getJavaResourceJar();
            }
            getBuilder().packageApk(
                    getResourceFile().getAbsolutePath(),
                    getDexFolders(),
                    resourceLocation,
                    getJniFolders(),
                    getMergingFolder(),
                    getAbiFilters(),
                    getJniDebugBuild(),
                    getSigningConfig(),
                    getPackagingOptions(),
                    getPackagingOptionsFilter(),
                    getOutputFile().getAbsolutePath());
        } catch (DuplicateFileException e) {
            Logger logger = getLogger();
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile()
                    .getAbsolutePath());
            logger.error("\tPath in archive: " + e.getArchivePath());
            logger.error("\tOrigin 1: " + e.getFile1());
            logger.error("\tOrigin 2: " + e.getFile2());
            logger.error("You can ignore those files in your build.gradle:");
            logger.error("\tandroid {");
            logger.error("\t  packagingOptions {");
            logger.error("\t    exclude \'" + e.getArchivePath() + "\'");
            logger.error("\t  }");
            logger.error("\t}");
            throw new BuildException(e.getMessage(), e);
        } catch (Exception e) {
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

        public ConfigAction(VariantOutputScope scope) {
            this.scope = scope;
        }

        @Override
        public String getName() {
            return scope.getTaskName("package");
        }

        @Override
        public Class<PackageApplication> getType() {
            return PackageApplication.class;
        }

        @Override
        public void execute(PackageApplication packageApp) {
            final ApkVariantData variantData = (ApkVariantData) scope.getVariantScope().getVariantData();
            final ApkVariantOutputData variantOutputData = (ApkVariantOutputData) scope
                    .getVariantOutputData();
            final GradleVariantConfiguration config = scope.getVariantScope().getVariantConfiguration();

            variantOutputData.packageApplicationTask = packageApp;
            packageApp.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            packageApp.setVariantName(
                    scope.getVariantScope().getVariantConfiguration().getFullName());

            if (config.isMinifyEnabled() && config.getBuildType().isShrinkResources() && !config
                    .getUseJack()) {
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

            ConventionMappingHelper.map(packageApp, "dexFolders", new Callable< Map<File, Format>>() {
                @Override
                public  Map<File, Format> call() {
                    return scope.getVariantScope().getTransformManager().getPipelineOuput(
                            sDexFilter, null);
                }
            });

            ConventionMappingHelper.map(packageApp, "javaResourceDir", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getVariantScope().getTransformManager().getSinglePipelineOutput(
                            sResFilter, Format.SINGLE_FOLDER);
                }
            });

            ConventionMappingHelper.map(packageApp, "javaResourceJar", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getVariantScope().getTransformManager().getSinglePipelineOutput(
                            sResFilter, Format.SINGLE_JAR);
                }
            });

            packageApp.setMergingFolder(new File(scope.getGlobalScope().getIntermediatesDir(),
                    variantOutputData.getFullName() + "/merging"));


            ConventionMappingHelper.map(packageApp, "jniFolders", new Callable<Set<File>>() {
                @Override
                public Set<File> call() {

                    if (variantData.getSplitHandlingPolicy() ==
                            BaseVariantData.SplitHandlingPolicy.PRE_21_POLICY) {
                        return scope.getVariantScope().getJniFolders();
                    }
                    Set<String> filters = AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
                    return filters.isEmpty() ? scope.getVariantScope().getJniFolders() : Collections.<File>emptySet();

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
                    return config.getSupportedAbis();
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
                            scope.getVariantScope().getVariantConfiguration().getFullName());
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

            ConventionMappingHelper.map(packageApp, "packagingOptionsFilter",
                    new Callable<SignedJarBuilder.IZipEntryFilter>() {
                @Override
                public SignedJarBuilder.IZipEntryFilter call() throws Exception {
                    return scope.getVariantScope().getPackagingOptionsFilter();
                }
            });

            ConventionMappingHelper.map(packageApp, "outputFile", new Callable<File>() {
                @Override
                public File call() throws Exception {
                    return scope.getPackageApk();
                }
            });
        }

        private static File getOptionalDir(File dir) {
            if (dir.isDirectory()) {
                return dir;
            }

            return null;
        }
    }
}
