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

import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.builder.dependency.LibraryBundle.FN_PROGUARD_TXT;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.LibraryJarTransform;
import com.android.build.gradle.internal.tasks.LibraryJniLibsTransform;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
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
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

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
            DataBindingBuilder dataBindingBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, dataBindingBuilder, extension, sdkHandler,dependencyManager, toolingRegistry);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        final LibraryVariantData libVariantData = (LibraryVariantData) variantData;
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final CoreBuildType buildType = variantConfig.getBuildType();

        final VariantScope variantScope = variantData.getScope();
        GlobalScope globalScope = variantScope.getGlobalScope();

        final File intermediatesDir = globalScope.getIntermediatesDir();
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

        // Add data binding tasks if enabled
        if (extension.getDataBinding().isEnabled()) {
            createDataBindingTasks(tasks, variantScope);
        }

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (isNdkTaskNeeded) {
            // Add NDK tasks
            ThreadRecorder.get().record(ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() throws Exception {
                            createNdkTasks(variantScope);
                            return null;
                        }
                    });
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // merge jni libs.
        createMergeJniLibFoldersTasks(tasks, variantScope);

        Sync packageRenderscript = ThreadRecorder.get().record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGING_TASK,
                new Recorder.Block<Sync>() {
                    @Override
                    public Sync call() throws Exception {
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
                globalScope.getIntermediatesDir(),
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
                                        "",
                                        SyncIssue.TYPE_GENERIC,
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
                        }

                        // now add a transform that will take all the class/res and package them
                        // into the main and secondary jar files.
                        // This transform technically does not use its transform output, but that's
                        // ok. We use the transform mechanism to get incremental data from
                        // the streams.

                        String packageName = variantConfig.getPackageFromManifest();
                        if (packageName == null) {
                            throw new BuildException("Failed to read manifest", null);
                        }

                        LibraryJarTransform transform = new LibraryJarTransform(
                                new File(variantBundleDir, FN_CLASSES_JAR),
                                new File(variantBundleDir, LIBS_FOLDER),
                                packageName,
                                getExtension().getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, transform);

                        AndroidTask<TransformTask> jarPackagingTask = transformManager
                                .addTransform(tasks, variantScope, transform);
                        bundle.dependsOn(jarPackagingTask.getName());

                        // now add a transform that will take all the native libs and package
                        // them into the libs folder of the bundle.
                        LibraryJniLibsTransform jniTransform = new LibraryJniLibsTransform(
                                new File(variantBundleDir, FD_JNI));
                        AndroidTask<TransformTask> jniPackagingTask = transformManager
                                .addTransform(tasks, variantScope, jniTransform);
                        bundle.dependsOn(jniPackagingTask.getName());

                        return null;
                    }
                });

        bundle.dependsOn(packageRes.getName(), packageRenderscript, lintCopy,
                mergeProGuardFileTask);
        bundle.dependsOn(variantScope.getNdkBuildable());

        bundle.setDescription("Assembles a bundle containing the library in " +
                variantConfig.getFullName() + ".");
        bundle.setDestinationDir(
                new File(globalScope.getOutputsDir(), BuilderConstants.EXT_LIB_ARCHIVE));
        bundle.setArchiveName(globalScope.getProjectBaseName()
                + "-" + variantConfig.getBaseName()
                + "." + BuilderConstants.EXT_LIB_ARCHIVE);
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

    private void excludeDataBindingClassesIfNecessary(final VariantScope variantScope,
            LibraryJarTransform transform) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        transform.addExcludeListProvider(
                new LibraryJarTransform.ExcludeListProvider() {
                    @Nullable
                    @Override
                    public List<String> getExcludeList() {
                        final File excludeFile = variantScope.getVariantData().getType()
                                .isExportDataBindingClassList() ? variantScope
                                .getGeneratedClassListOutputFileForDataBinding() : null;
                        return dataBindingBuilder.getJarExcludeList(
                                variantScope.getVariantData().getLayoutXmlProcessor(), excludeFile
                        );
                    }
                });
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        return TransformManager.SCOPE_FULL_LIBRARY;
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
