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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.build.transform.api.ScopedContent.ContentType.CLASSES;
import static com.android.build.transform.api.ScopedContent.ContentType.RESOURCES;
import static com.android.build.transform.api.ScopedContent.Scope.PROJECT;
import static com.android.build.transform.api.ScopedContent.Scope.PROJECT_LOCAL_DEPS;
import static com.android.builder.dependency.LibraryBundle.FN_PROGUARD_TXT;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.internal.tasks.SingleFileCopyTask;
import com.android.build.gradle.internal.transforms.MultiStreamJarTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.transform.api.ScopedContent;
import com.android.build.transform.api.ScopedContent.ContentType;
import com.android.build.transform.api.ScopedContent.Scope;
import com.android.build.transform.api.Transform;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.LibraryBundle;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.dependency.ManifestDependency;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.ExecutionType;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * TaskManager for creating tasks in an Android library project.
 */
public class LibraryTaskManager extends TaskManager {

    private static final String ANNOTATIONS = "annotations";

    private Task assembleDefault;

    public LibraryTaskManager (
            Project project,
            AndroidBuilder androidBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, extension, sdkHandler, dependencyManager, toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        final LibraryVariantData libVariantData = (LibraryVariantData) variantData;
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final CoreBuildType buildType = variantConfig.getBuildType();

        final VariantScope variantScope = variantData.getScope();

        final File intermediatesDir = variantScope.getGlobalScope().getIntermediatesDir();
        final Collection<String> variantDirectorySegments = variantConfig.getDirectorySegments();
        final File variantBundleDir = FileUtils.join(
                intermediatesDir,
                StringHelper.toStrings(DIR_BUNDLES, variantDirectorySegments));

        createAnchorTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        createCheckManifestTask(tasks, variantScope);

        // Add a task to create the res values
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createGenerateResValuesTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to process the manifest(s)
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createMergeLibManifestsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to compile renderscript files.
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createRenderscriptTask(tasks, variantScope);
                        return null;
                    }
                });

        AndroidTask<MergeResources> packageRes = ThreadRecorder.get().record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                new Recorder.Block<AndroidTask<MergeResources>>() {
                    @Override
                    public AndroidTask<MergeResources> call() throws Exception {
                        // Create a merge task to only merge the resources from this library and not
                        // the dependencies. This is what gets packaged in the aar.
                        AndroidTask<MergeResources> mergeResourceTask =
                                basicCreateMergeResourcesTask(
                                        tasks,
                                        variantScope,
                                        "package",
                                        FileUtils.join(variantBundleDir, "res"),
                                        false /*includeDependencies*/,
                                        false /*process9Patch*/);

                        if (variantData.getVariantDependency().hasNonOptionalLibraries()) {
                            // Add a task to merge the resource folders, including the libraries, in order to
                            // generate the R.txt file with all the symbols, including the ones from
                            // the dependencies.
                            createMergeResourcesTask(tasks, variantScope);
                        }

                        mergeResourceTask.configure(tasks,
                                new Action<Task>() {
                                    @Override
                                    public void execute(Task task) {
                                        MergeResources mergeResourcesTask = (MergeResources) task;
                                        mergeResourcesTask.setPublicFile(FileUtils.join(
                                                variantBundleDir,
                                                SdkConstants.FN_PUBLIC_TXT
                                        ));
                                    }
                                });

                        return mergeResourceTask;
                    }
                });

        // Add a task to merge the assets folders
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() {
                        createMergeAssetsTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a task to create the BuildConfig class
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createBuildConfigTask(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        // Add a task to generate resource source files, directing the location
                        // of the r.txt file to be directly in the bundle.
                        createProcessResTask(tasks, variantScope, variantBundleDir,
                                false /*generateResourcePackage*/);

                        // process java resources
                        createProcessJavaResTasks(tasks, variantScope);
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_AIDL_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createAidlTask(tasks, variantScope);
                        return null;
                    }
                });

        // Add a compile task
        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_COMPILE_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        AndroidTask<JavaCompile> javacTask = createJavacTask(tasks, variantScope);
                        TaskManager.setJavaCompilerTask(javacTask, tasks, variantScope);
                        return null;
                    }
                });

        // package the prebuilt native libs into the bundle folder
        final Sync packageJniLibs = project.getTasks().create(
                variantScope.getTaskName("package", "JniLibs"),
                Sync.class);

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (isNdkTaskNeeded) {
            // Add NDK tasks
            ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() throws Exception {
                            createNdkTasks(variantScope);
                            packageJniLibs.dependsOn(variantData.ndkCompileTask);
                            packageJniLibs.from(variantData.ndkCompileTask.getSoFolder())
                                    .include("**/*.so");
                            return null;
                        }
                    });
        } else {
            if (variantData.compileTask != null) {
                variantData.compileTask.dependsOn(getNdkBuildable(variantData));
            } else {
                variantScope.getCompileTask().dependsOn(tasks, getNdkBuildable(variantData));
            }
            packageJniLibs.dependsOn(getNdkBuildable(variantData));
            packageJniLibs.from(variantScope.getNdkSoFolder())
                    .include("**/*.so");
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        Sync packageRenderscript = ThreadRecorder.get().record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGING_TASK,
                new Recorder.Block<Sync>() {
                    @Override
                    public Sync call() throws Exception {
                        // package from 2 sources.
                        packageJniLibs.from(variantConfig.getJniLibsList())
                                .include("**/*.so");
                        packageJniLibs.into(new File(variantBundleDir, "jni"));

                        // package the renderscript header files files into the bundle folder
                        Sync packageRenderscript = project.getTasks().create(
                                variantScope.getTaskName("package", "Renderscript"), Sync.class);
                        // package from 3 sources. the order is important to make sure the override works well.
                        packageRenderscript.from(variantConfig.getRenderscriptSourceList())
                                .include("**/*.rsh");
                        packageRenderscript.into(new File(variantBundleDir, FD_RENDERSCRIPT));
                        return packageRenderscript;
                    }
                });

        // merge consumer proguard files from different build types and flavors
        MergeFileTask mergeProGuardFileTask = ThreadRecorder.get().record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK,
                new Recorder.Block<MergeFileTask>() {
                    @Override
                    public MergeFileTask call() throws Exception {
                        MergeFileTask mergeProGuardFileTask = project.getTasks().create(
                                variantScope.getTaskName("merge", "ProguardFiles"),
                                MergeFileTask.class);
                        mergeProGuardFileTask.setVariantName(variantConfig.getFullName());
                        mergeProGuardFileTask.setInputFiles(
                                project.files(variantConfig.getConsumerProguardFiles())
                                        .getFiles());
                        mergeProGuardFileTask.setOutputFile(
                                new File(variantBundleDir, FN_PROGUARD_TXT));
                        return mergeProGuardFileTask;
                    }

                });

        // copy lint.jar into the bundle folder
        Copy lintCopy = project.getTasks().create(
                variantScope.getTaskName("copy", "Lint"), Copy.class);
        lintCopy.dependsOn(LINT_COMPILE);
        lintCopy.from(new File(
                variantScope.getGlobalScope().getIntermediatesDir(),
                "lint/lint.jar"));
        lintCopy.into(variantBundleDir);

        final Zip bundle = project.getTasks().create(variantScope.getTaskName("bundle"), Zip.class);

        if (variantData.getVariantDependency().isAnnotationsPresent()) {
            libVariantData.generateAnnotationsTask =
                    createExtractAnnotations(project, variantData);
        }
        if (libVariantData.generateAnnotationsTask != null) {
            bundle.dependsOn(libVariantData.generateAnnotationsTask);
        }

        final boolean instrumented = variantConfig.getBuildType().isTestCoverageEnabled();

        ThreadRecorder.get().record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        TransformManager transformManager = variantScope.getTransformManager();

                        // ----- Code Coverage first -----
                        if (instrumented) {
                            createJacocoTransform(tasks, variantScope);
                        }

                        // ----- External Transforms -----
                        // apply all the external transforms.
                        List<Transform> customTransforms = extension.getTransforms();
                        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

                        for (int i = 0, count = customTransforms.size() ; i < count ; i++) {
                            Transform transform = customTransforms.get(i);

                            // Check the transform only applies to supported scopes for libraries:
                            // We cannot transform scopes that are not packaged in the library
                            // itself.
                            Sets.SetView<Scope> difference = Sets.difference(transform.getScopes(),
                                    TransformManager.SCOPE_FULL_LIBRARY);
                            if (!difference.isEmpty()) {
                                String scopes = difference.toString();
                                androidBuilder.getErrorReporter().handleSyncError(
                                        scopes,
                                        SyncIssue.TYPE_WRONG_TRANSFORM_SCOPE_ON_LIB,
                                        String.format("Transforms with scopes '%s' cannot be applied to library projects.",
                                                scopes));
                            }

                            AndroidTask<TransformTask> task = transformManager
                                    .addTransform(tasks, variantScope, transform);
                            List<Object> deps = customTransformsDependencies.get(i);
                            if (!deps.isEmpty()) {
                                task.dependsOn(tasks, deps);
                            }
                        }

                        // ----- Minify next -----
                        if (buildType.isMinifyEnabled()) {
                            createMinifyTransform(tasks, variantScope, false);

                            // we're going to get a single stream with all the stuff we need.
                            // Find it.
                            TransformStream stream = transformManager.getSingleStream(
                                    new TransformManager.StreamFilter() {
                                        @Override
                                        public boolean accept(
                                                @NonNull Set<ContentType> types,
                                                @NonNull Set<Scope> scopes) {
                                            return scopes.equals(
                                                    TransformManager.SCOPE_FULL_LIBRARY) &&
                                                    types.equals(TransformManager.CONTENT_JARS);
                                        }
                                    });

                            // jar/copy it.
                            handleMainClassJar(
                                    stream,
                                    ImmutableList.<TransformStream>of(),
                                    variantScope,
                                    variantConfig,
                                    variantBundleDir,
                                    libVariantData,
                                    bundle);
                        } else {
                            // get all the streams we need to bundle.
                            // Look for the class and resources separately. What matters is whether
                            // the classes are together or not (the resources are always merged so
                            // we always have a single res stream.
                            // Note that we could really get any type of streams here due to
                            // third party transforms.
                            List<TransformStream> classStreams = transformManager.getStreams(
                                    new TransformManager.StreamFilter() {
                                        @Override
                                        public boolean accept(
                                                @NonNull Set<ContentType> types,
                                                @NonNull Set<Scope> scopes) {
                                            return (scopes.contains(PROJECT) ||
                                                    scopes.contains(PROJECT_LOCAL_DEPS)) &&
                                                    types.contains(CLASSES);
                                        }
                                    });
                            List<TransformStream> resStreams = transformManager.getStreams(
                                    new TransformManager.StreamFilter() {
                                        @Override
                                        public boolean accept(
                                                @NonNull Set<ContentType> types,
                                                @NonNull Set<Scope> scopes) {
                                            return (scopes.contains(PROJECT) ||
                                                    scopes.contains(PROJECT_LOCAL_DEPS)) &&
                                                    types.contains(RESOURCES);
                                        }
                                    });

                            if (classStreams.size() == 1) {
                                // all code is combined, create a single jar task, using all the
                                // streams, or if the output is already a jar, just copy it where
                                // it needs to go.
                                handleMainClassJar(
                                        Iterables.getOnlyElement(classStreams),
                                        resStreams,
                                        variantScope,
                                        variantConfig,
                                        variantBundleDir,
                                        libVariantData,
                                        bundle);

                            } else {
                                // looks like the code and the libraries are separate, we're going
                                // to jar them separately, and package the local jars in the
                                // libs folder.
                                // All the merged resources will go in with the main jar file.

                                // 1. find the main code stream.
                                TransformStream mainClassStreams = transformManager
                                        .getSingleStream(
                                                new TransformManager.StreamFilter() {
                                                    @Override
                                                    public boolean accept(
                                                            @NonNull Set<ContentType> types,
                                                            @NonNull Set<Scope> scopes) {
                                                        return scopes.contains(PROJECT) &&
                                                                types.contains(CLASSES);
                                                    }
                                                });

                                // jar/copy it.
                                handleMainClassJar(
                                        mainClassStreams,
                                        resStreams,
                                        variantScope,
                                        variantConfig,
                                        variantBundleDir,
                                        libVariantData,
                                        bundle);

                                // 2. now handle the remaining local jars as a transform
                                // to more easily create a bunch of jars.

                                // Create the sync task first that will copy from the jars temp
                                // location to the final location, taking care of removing
                                // obsolete jars.
                                Sync syncLocalJars = project.getTasks().create(
                                        variantScope.getTaskName(
                                                "sync",
                                                "LocalJar"),
                                        Sync.class);
                                syncLocalJars.into(new File(variantBundleDir, LIBS_FOLDER));
                                bundle.dependsOn(syncLocalJars);

                                transformManager.addTransform(
                                        tasks, variantScope,
                                        new MultiStreamJarTransform());

                                // get the resulting stream to find the location.
                                List<TransformStream> localClassStreams = transformManager
                                        .getStreams(
                                                new TransformManager.StreamFilter() {
                                                    @Override
                                                    public boolean accept(
                                                            @NonNull Set<ContentType> types,
                                                            @NonNull Set<Scope> scopes) {
                                                        return scopes.contains(PROJECT_LOCAL_DEPS)
                                                                &&
                                                                types.contains(CLASSES);
                                                    }
                                                });

                                // update the sync task with source and dependencies.
                                // there should be only one stream anyway.
                                for (TransformStream stream : localClassStreams) {
                                    syncLocalJars.from(stream.getFiles().get());
                                    syncLocalJars.dependsOn(stream.getDependencies());
                                }
                            }
                        }

                        return null;
                    }
                });

        bundle.dependsOn(packageRes.getName(), packageRenderscript, lintCopy, packageJniLibs,
                mergeProGuardFileTask);
        bundle.dependsOn(variantScope.getNdkBuildable());

        bundle.setDescription("Assembles a bundle containing the library in " +
                variantConfig.getFullName() + ".");
        bundle.setDestinationDir(new File(variantScope.getGlobalScope().getOutputsDir(), "aar"));
        bundle.setArchiveName(project.getName() + "-" + variantConfig.getBaseName() + "."
                + BuilderConstants.EXT_LIB_ARCHIVE);
        bundle.setExtension(BuilderConstants.EXT_LIB_ARCHIVE);
        bundle.from(variantBundleDir);
        bundle.from(FileUtils.join(intermediatesDir,
                StringHelper.toStrings(ANNOTATIONS, variantDirectorySegments)));

        // get the single output for now, though that may always be the case for a library.
        LibVariantOutputData variantOutputData = libVariantData.getOutputs().get(0);
        variantOutputData.packageLibTask = bundle;

        variantData.assembleVariantTask.dependsOn(bundle);
        variantOutputData.assembleTask = variantData.assembleVariantTask;

        if (getExtension().getDefaultPublishConfig().equals(variantConfig.getFullName())) {
            VariantHelper.setupDefaultConfig(project,
                    variantData.getVariantDependency().getPackageConfiguration());

            // add the artifact that will be published
            project.getArtifacts().add("default", bundle);

            getAssembleDefault().dependsOn(variantData.assembleVariantTask);
        }

        // also publish the artifact with its full config name
        if (getExtension().getPublishNonDefault()) {
            project.getArtifacts().add(
                    variantData.getVariantDependency().getPublishConfiguration().getName(), bundle);
            bundle.setClassifier(
                    variantData.getVariantDependency().getPublishConfiguration().getName());
        }

        // configure the variant to be testable.
        variantConfig.setOutput(new LibraryBundle(
                bundle.getArchivePath(),
                variantBundleDir,
                variantData.getName(),
                project.getPath()) {
            @Override
            @Nullable
            public String getProjectVariant() {
                return variantData.getName();
            }

            @NonNull
            @Override
            public List<LibraryDependency> getDependencies() {
                return variantConfig.getDirectLibraries();
            }

            @NonNull
            @Override
            public List<? extends AndroidLibrary> getLibraryDependencies() {
                return variantConfig.getDirectLibraries();
            }

            @NonNull
            @Override
            public List<? extends ManifestDependency> getManifestDependencies() {
                return variantConfig.getDirectLibraries();
            }

            @Override
            @Nullable
            public MavenCoordinates getRequestedCoordinates() {
                return null;
            }

            @Override
            @Nullable
            public MavenCoordinates getResolvedCoordinates() {
                return null;
            }

            @Override
            @NonNull
            protected File getJarsRootFolder() {
                return getFolder();
            }

            @Override
            public boolean isOptional() {
                return false;
            }

            @NonNull
            @Override
            public List<JarDependency> getLocalDependencies() {
                // because the test artifact already directly depends on the local jars
                // we make this fake AndroidLibrary not include them, otherwise
                // they'll be there twice.
                return ImmutableList.of();
            }

            @NonNull
            @Override
            public List<File> getLocalJars() {
                // because the test artifact already directly depends on the local jars
                // we make this fake AndroidLibrary not include them, otherwise
                // they'll be there twice.
                return ImmutableList.of();
            }
        });

        ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_LINT_TASK,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createLintTasks(tasks, variantScope);
                        return null;
                    }
                });
    }

    private void handleMainClassJar(
            TransformStream classStream,
            List<TransformStream> resStreams,
            VariantScope variantScope,
            GradleVariantConfiguration variantConfig,
            File variantBundleDir,
            LibraryVariantData libVariantData,
            Zip bundle) {
        ScopedContent.Format format = classStream.getFormat();
        switch (format) {
            case SINGLE_FOLDER:
                // jar the content.
                Jar jar = createMainJarTask(
                        ImmutableList.of(classStream),
                        resStreams,
                        variantScope,
                        variantConfig,
                        variantBundleDir,
                        libVariantData);
                bundle.dependsOn(jar);
                break;
            case MULTI_FOLDER:
                throw new RuntimeException("Unsupported ScopedContent.Format value: " + format.name());
            case JAR:
                // sync the content.
                // TODO Need to fix the case where this jar still have R!!!
                SingleFileCopyTask copyTask = project.getTasks().create(
                        variantScope.getTaskName("copyMain", "Jar"),
                        SingleFileCopyTask.class);
                copyTask.setInputFile(Iterables.getOnlyElement(
                        classStream.getFiles().get()));
                copyTask.setOutputFile(
                        new File(variantBundleDir, FN_CLASSES_JAR));
                copyTask.dependsOn(classStream.getDependencies());
                bundle.dependsOn(copyTask);
                break;
            default:
                throw new RuntimeException("Unsupported ScopedContent.Format value: " + format.name());
        }
    }

    @NonNull
    private Jar createMainJarTask(
            @NonNull List<TransformStream> streamList1,
            @NonNull List<TransformStream> streamList2,
            @NonNull VariantScope variantScope,
            @NonNull GradleVariantConfiguration variantConfig,
            @NonNull File variantBundleDir,
            @NonNull LibraryVariantData libVariantData) {
        Jar jarTask = createJarTask(streamList1,
                streamList2,
                variantScope,
                "ClassesJar",
                variantBundleDir,
                FN_CLASSES_JAR);

        String packageName = variantConfig.getPackageFromManifest();
        if (packageName == null) {
            throw new BuildException("Failed to read manifest", null);
        }

        packageName = packageName.replace(".", "/");

        jarTask.exclude(packageName + "/R.class");
        jarTask.exclude(packageName + "/R$*.class");
        if (!getExtension().getPackageBuildConfig()) {
            jarTask.exclude(packageName + "/Manifest.class");
            jarTask.exclude(packageName + "/Manifest$*.class");
            jarTask.exclude(packageName + "/BuildConfig.class");
        }

        if (libVariantData.generateAnnotationsTask != null) {
            // In case extract annotations strips out private typedef annotation classes
            jarTask.dependsOn(libVariantData.generateAnnotationsTask);
        }

        return jarTask;
    }

    @NonNull
    private Jar createJarTask(
            @NonNull List<TransformStream> streamList1,
            @NonNull List<TransformStream> streamList2,
            @NonNull VariantScope variantScope,
            @NonNull String suffix,
            @NonNull File destinationDir,
            @NonNull String jarName) {
        Jar jar = project.getTasks().create(
                variantScope.getTaskName("package", suffix), Jar.class);
        jar.setEntryCompression(ZipEntryCompression.STORED);

        for (TransformStream stream : Iterables.concat(streamList1, streamList2)) {
            if (stream.getFormat() == ScopedContent.Format.JAR || stream.getFormat() == ScopedContent.Format.MULTI_FOLDER) {
                throw new RuntimeException("Cannot create jar task for stream: " + stream);
            }

            jar.from(stream.getFiles().get());
            jar.dependsOn(stream.getDependencies());
        }

        jar.setDestinationDir(destinationDir);
        jar.setArchiveName(jarName);

        return jar;
    }

    @NonNull
    @Override
    protected Set<Scope> computeExtractResAndJavaFromJarScopes(
            @NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            // if it's the test app for a library, behave like an app rather than a library.
            return ApplicationTaskManager.computeExtractResAndJavaFromJarScopes2(variantScope);
        }

        // otherwise always extract everything so that we can merge the local jars anyway.
        return Sets.immutableEnumSet(PROJECT_LOCAL_DEPS);
    }

    @NonNull
    @Override
    protected Set<Scope> computeExtractResFromJarScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            // if it's the test app for a library, behave like an app rather than a library.
            return ApplicationTaskManager.computeExtractResFromJarScopes(variantScope, this);
        }

        return TransformManager.EMPTY_SCOPES;
    }

    public ExtractAnnotations createExtractAnnotations(
            final Project project,
            final BaseVariantData variantData) {
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        final ExtractAnnotations task = project.getTasks().create(
                variantData.getScope().getTaskName("extract", "Annotations"),
                ExtractAnnotations.class);
        task.setDescription(
                "Extracts Android annotations for the " + variantData.getVariantConfiguration()
                        .getFullName()
                        + " variant into the archive file");
        task.setGroup(BasePlugin.BUILD_GROUP);
        task.variant = variantData;
        task.setDestinationDir(new File(
                variantData.getScope().getGlobalScope().getIntermediatesDir(),
                ANNOTATIONS + "/" + config.getDirName()));
        task.output = new File(task.getDestinationDir(), FN_ANNOTATIONS_ZIP);
        task.classDir = new File(variantData.getScope().getGlobalScope().getIntermediatesDir(),
                "classes/" + variantData.getVariantConfiguration().getDirName());
        task.setSource(variantData.getJavaSources());
        task.encoding = getExtension().getCompileOptions().getEncoding();
        task.setSourceCompatibility(
                getExtension().getCompileOptions().getSourceCompatibility().toString());
        ConventionMappingHelper.map(task, "classpath", new Callable<ConfigurableFileCollection>() {
            @Override
            public ConfigurableFileCollection call() throws Exception {
                return project.files(androidBuilder.getCompileClasspath(config));
            }
        });
        task.dependsOn(variantData.getScope().getJavacTask().getName());

        // Setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed. (Same as in compileTask)
        task.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (task instanceof ExtractAnnotations) {
                    ExtractAnnotations extractAnnotations = (ExtractAnnotations) task;
                    extractAnnotations.bootClasspath = androidBuilder.getBootClasspathAsStrings(false);
                }
            }
        });

        return task;

    }


    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.getTasks().findByName("assembleDefault");
        }
        return assembleDefault;
    }
}
