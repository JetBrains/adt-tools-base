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

package com.android.build.gradle.internal;

import static com.android.build.OutputFile.DENSITY;
import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreJackOptions;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.BuildInfoLoaderTask;
import com.android.build.gradle.internal.incremental.InstantRunAnchorTaskConfigAction;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.incremental.InstantRunWrapperTask;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.StreamFilter;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformStream;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.ApkPublishArtifact;
import com.android.build.gradle.internal.publishing.ManifestPublishArtifact;
import com.android.build.gradle.internal.publishing.MappingPublishArtifact;
import com.android.build.gradle.internal.publishing.MetadataPublishArtifact;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.AndroidTaskRegistry;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.SupplierTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.DependencyReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.tasks.ExtractProguardFiles;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.InstallVariantTask;
import com.android.build.gradle.internal.tasks.JackJacocoReportTask;
import com.android.build.gradle.internal.tasks.LintCompile;
import com.android.build.gradle.internal.tasks.MockableAndroidJarTask;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.SigningReportTask;
import com.android.build.gradle.internal.tasks.SourceSetsTask;
import com.android.build.gradle.internal.tasks.TestServerTask;
import com.android.build.gradle.internal.tasks.UninstallTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.tasks.databinding.DataBindingProcessLayoutsTask;
import com.android.build.gradle.internal.tasks.multidex.CreateManifestKeepList;
import com.android.build.gradle.internal.test.TestDataImpl;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.transforms.ExtractJarsTransform;
import com.android.build.gradle.internal.transforms.JackTransform;
import com.android.build.gradle.internal.transforms.JacocoTransform;
import com.android.build.gradle.internal.transforms.JarMergingTransform;
import com.android.build.gradle.internal.transforms.MergeJavaResourcesTransform;
import com.android.build.gradle.internal.transforms.MultiDexTransform;
import com.android.build.gradle.internal.transforms.NewShrinkerTransform;
import com.android.build.gradle.internal.transforms.ProGuardTransform;
import com.android.build.gradle.internal.transforms.ProguardConfigurable;
import com.android.build.gradle.internal.transforms.ShrinkResourcesTransform;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.SplitHandlingPolicy;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.CompatibleScreensManifest;
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.GenerateResValues;
import com.android.build.gradle.tasks.GenerateSplitAbiRes;
import com.android.build.gradle.tasks.JackPreDexTransform;
import com.android.build.gradle.tasks.Lint;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeManifests;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.NdkCompile;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.PackageSplitAbi;
import com.android.build.gradle.tasks.PackageSplitRes;
import com.android.build.gradle.tasks.PreColdSwapTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.ProcessTestManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ShaderCompile;
import com.android.build.gradle.tasks.SplitZipAlign;
import com.android.build.gradle.tasks.ZipAlign;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.android.build.gradle.tasks.factory.IncrementalSafeguard;
import com.android.build.gradle.tasks.factory.JacocoAgentConfigAction;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.android.build.gradle.tasks.factory.PackageJarArtifactConfigAction;
import com.android.build.gradle.tasks.factory.ProcessJavaResConfigAction;
import com.android.build.gradle.tasks.factory.TestServerTaskConfigAction;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.VariantConfiguration;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.model.SyncIssue;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.manifmerger.ManifestMerger2;
import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.StringHelper;
import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import groovy.lang.Closure;

/**
 * Manages tasks creation.
 */
public abstract class TaskManager {

    public static final String DEFAULT_PROGUARD_CONFIG_FILE = "proguard-android.txt";

    public static final String DIR_BUNDLES = "bundles";

    public static final String INSTALL_GROUP = "Install";

    public static final String BUILD_GROUP = BasePlugin.BUILD_GROUP;

    public static final String ANDROID_GROUP = "Android";

    protected Project project;

    protected AndroidBuilder androidBuilder;

    protected DataBindingBuilder dataBindingBuilder;

    private DependencyManager dependencyManager;

    protected SdkHandler sdkHandler;

    protected AndroidConfig extension;

    protected ToolingModelBuilderRegistry toolingRegistry;

    private final GlobalScope globalScope;

    private final AndroidTaskRegistry androidTasks = new AndroidTaskRegistry();

    private Logger logger;

    protected boolean isComponentModelPlugin = false;

    // Task names
    // These cannot be AndroidTasks as in the component model world there is nothing to force
    // generateTasksBeforeEvaluate to happen before the variant tasks are created.
    private static final String MAIN_PREBUILD = "preBuild";

    private static final String UNINSTALL_ALL = "uninstallAll";

    private static final String DEVICE_CHECK = "deviceCheck";

    private static final String DEVICE_ANDROID_TEST = DEVICE + ANDROID_TEST.getSuffix();

    protected static final String CONNECTED_CHECK = "connectedCheck";

    private static final String CONNECTED_ANDROID_TEST = CONNECTED + ANDROID_TEST.getSuffix();

    private static final String ASSEMBLE_ANDROID_TEST = "assembleAndroidTest";

    private static final String SOURCE_SETS = "sourceSets";

    public static final String LINT = "lint";

    protected static final String LINT_COMPILE = "compileLint";

    private static final String EXTRACT_PROGUARD_FILES = "extractProguardFiles";

    private static final Revision MIN_REVISION_RS_COMPAT_64 = Revision.parseRevision("23.0.3");


    // Tasks
    private AndroidTask<Copy> jacocoAgentTask;

    public AndroidTask<MockableAndroidJarTask> createMockableJar;

    public TaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry) {
        this.project = project;
        this.androidBuilder = androidBuilder;
        this.dataBindingBuilder = dataBindingBuilder;
        this.sdkHandler = sdkHandler;
        this.extension = extension;
        this.toolingRegistry = toolingRegistry;
        this.dependencyManager = dependencyManager;
        logger = Logging.getLogger(this.getClass());

        globalScope = new GlobalScope(
                project,
                androidBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                toolingRegistry);
    }

    private boolean isDebugLog() {
        return project.getLogger().isEnabled(LogLevel.DEBUG);
    }

    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    /**
     * Creates the tasks for a given BaseVariantData.
     */
    public abstract void createTasksForVariantData(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData);

    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    /**
     * Returns a collection of buildables that creates native object.
     *
     * A buildable is considered to be any object that can be used as the argument to
     * Task.dependsOn.  This could be a Task or a BuildableModelElement (e.g. BinarySpec).
     */
    protected Collection<Object> getNdkBuildable(BaseVariantData variantData) {
        if (variantData.ndkCompileTask== null) {
            return Collections.emptyList();
        }
        return Collections.singleton(variantData.ndkCompileTask);
    }

    /**
     * Override to configure NDK data in the scope.
     */
    public void configureScopeForNdk(@NonNull VariantScope scope) {
        final BaseVariantData variantData = scope.getVariantData();
        scope.setNdkSoFolder(Collections.singleton(new File(
                scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/lib")));
        File objFolder = new File(scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/obj");
        scope.setNdkObjFolder(objFolder);
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(abi,
                    new File(objFolder, "local/" + abi.getName()));
        }

    }

    protected AndroidConfig getExtension() {
        return extension;
    }

    public void resolveDependencies(
            @NonNull VariantDependencies variantDeps,
            @Nullable VariantDependencies testedVariantDeps,
            @Nullable String testedProjectPath) {
        dependencyManager.resolveDependencies(variantDeps, testedVariantDeps, testedProjectPath);
    }

    /**
     * Create tasks before the evaluation (on plugin apply). This is useful for tasks that
     * could be referenced by custom build logic.
     */
    public void createTasksBeforeEvaluate(@NonNull TaskFactory tasks) {
        androidTasks.create(tasks, UNINSTALL_ALL, uninstallAllTask -> {
            uninstallAllTask.setDescription("Uninstall all applications.");
            uninstallAllTask.setGroup(INSTALL_GROUP);
        });

        androidTasks.create(tasks, DEVICE_CHECK, deviceCheckTask -> {
            deviceCheckTask.setDescription(
                    "Runs all device checks using Device Providers and Test Servers.");
            deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        });

        androidTasks.create(tasks, CONNECTED_CHECK, connectedCheckTask -> {
            connectedCheckTask.setDescription(
                    "Runs all device checks on currently connected devices.");
            connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        });

        androidTasks.create(tasks, MAIN_PREBUILD, task -> {});

        AndroidTask<ExtractProguardFiles> extractProguardFiles =
                androidTasks.create(
                        tasks, EXTRACT_PROGUARD_FILES, ExtractProguardFiles.class, task -> {});
        // Make sure MAIN_PREBUILD runs first:
        extractProguardFiles.dependsOn(tasks, MAIN_PREBUILD);

        androidTasks.create(tasks, new SourceSetsTask.ConfigAction(extension));

        androidTasks.create(tasks, ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
        });

        AndroidTask<Lint> globalLintTask = androidTasks.create(tasks,
                new Lint.GlobalConfigAction(globalScope));

        tasks.named(JavaBasePlugin.CHECK_TASK_NAME, it -> it.dependsOn(LINT));

        androidTasks.create(tasks, new LintCompile.ConfigAction(globalScope));
    }

    public void createMockableJarTask(TaskFactory tasks) {
        createMockableJar = androidTasks.create(tasks, new MockableAndroidJarTask.ConfigAction(globalScope));
    }

    protected void createDependencyStreams(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // content that can be found in a jar:
        Set<ContentType> fullJar = ImmutableSet.of(
                DefaultContentType.CLASSES, DefaultContentType.RESOURCES, ExtendedContentType.NATIVE_LIBS);

        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(fullJar)
                .addScope(Scope.PROJECT_LOCAL_DEPS)
                .setJars(config::getLocalPackagedJars)
                .build());

        IncrementalMode incrementalMode = getIncrementalMode(config);
        boolean skipDependency = incrementalMode == IncrementalMode.LOCAL_JAVA_ONLY ||
                incrementalMode == IncrementalMode.LOCAL_RES_ONLY;
        ImmutableList<Object> dependencies = skipDependency ?
                ImmutableList.of() :
                ImmutableList.of(variantScope.getPrepareDependenciesTask().getName(),
                        variantData.getVariantDependency().getPackageConfiguration()
                                .getBuildDependencies());

        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_JARS)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setJars(() -> Stream.concat(
                                config.getExternalPackagedJars().stream(),
                                variantScope.getGlobalScope().getAndroidBuilder()
                                        .getAdditionalPackagedJars(config).stream())
                        .collect(Collectors.toSet()))
                .setDependencies(dependencies)
                .build());

        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                .addScope(Scope.EXTERNAL_LIBRARIES)
                .setJars(() -> Stream.concat(
                                config.getExternalPackagedJniJars().stream(),
                                variantScope.getGlobalScope().getAndroidBuilder()
                        .getAdditionalPackagedJars(config).stream())
                        .collect(Collectors.toSet()))
                .setFolders(config::getExternalAarJniLibFolders)
                .setDependencies(dependencies)
                .build());

        // for the sub modules, only the main jar has resources.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_JARS)
                .addScope(Scope.SUB_PROJECTS)
                .setJars(config::getSubProjectPackagedJars)
                .setDependencies(dependencies)
                .build());

        // the local deps don't have resources (been merged into the main jar)
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_CLASS)
                .addScope(Scope.SUB_PROJECTS_LOCAL_DEPS)
                .setJars(config::getSubProjectLocalPackagedJars)
                .setDependencies(dependencies)
                .build());

        // and the native libs of the libraries are in a separate folder.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(TransformManager.CONTENT_NATIVE_LIBS)
                .addScope(Scope.SUB_PROJECTS)
                .setFolders(config::getSubProjectJniLibFolders)
                .setJars(config::getSubProjectPackagedJniJars)
                .setDependencies(dependencies)
                .build());

        // provided only scopes.
        transformManager.addStream(OriginalStream.builder()
                .addContentTypes(fullJar)
                .addScope(Scope.PROVIDED_ONLY)
                .setJars(config::getProvidedOnlyJars)
                .build());

        if (variantScope.getTestedVariantData() != null) {
            final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

            VariantScope testedVariantScope = testedVariantData.getScope();

            // create two streams of different types.
            transformManager.addStream(OriginalStream.builder()
                    .addContentTypes(DefaultContentType.CLASSES)
                    .addScope(Scope.TESTED_CODE)
                    .setFolders(Suppliers.ofInstance(
                            (Collection<File>) ImmutableList.of(testedVariantScope.getJavaOutputDir())))
                    .setDependency(testedVariantScope.getJavacTask().getName())
                    .build());

            transformManager.addStream(OriginalStream.builder()
                    .addContentTypes(DefaultContentType.CLASSES)
                    .addScope(Scope.TESTED_CODE)
                    .setJars(() -> variantScope.getGlobalScope().getAndroidBuilder()
                            .getAllPackagedJars(testedVariantData.getVariantConfiguration()))
                    .setDependency(ImmutableList.of(
                            testedVariantScope.getPrepareDependenciesTask().getName(),
                            testedVariantData.getVariantDependency().getPackageConfiguration()
                                    .getBuildDependencies()))
                    .build());
        }

        handleJacocoDependencies(tasks, variantScope);
    }

    public void createMergeAppManifestsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        ApplicationVariantData appVariantData =
                (ApplicationVariantData) variantScope.getVariantData();
        Set<String> screenSizes = appVariantData.getCompatibleScreens();

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated manifest
        for (final BaseVariantOutputData vod : appVariantData.getOutputs()) {
            VariantOutputScope scope = vod.getScope();

            AndroidTask<CompatibleScreensManifest> csmTask = null;
            if (vod.getMainOutputFile().getFilter(DENSITY) != null) {
                csmTask = androidTasks.create(tasks,
                        new CompatibleScreensManifest.ConfigAction(scope, screenSizes));
                scope.setCompatibleScreensManifestTask(csmTask);
            }

            List<ManifestMerger2.Invoker.Feature> optionalFeatures = getIncrementalMode(
                    variantScope.getVariantConfiguration()) != IncrementalMode.NONE
                    ? ImmutableList.of(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT)
                    : ImmutableList.of();

            AndroidTask<? extends ManifestProcessorTask> processManifestTask =
                    androidTasks.create(tasks, getMergeManifestConfig(scope, optionalFeatures));
            scope.setManifestProcessorTask(processManifestTask);

            processManifestTask.dependsOn(tasks, variantScope.getPrepareDependenciesTask());

            if (variantScope.getMicroApkTask() != null) {
                processManifestTask.dependsOn(tasks, variantScope.getMicroApkTask());
            }

            if (csmTask != null) {
                processManifestTask.dependsOn(tasks, csmTask);
            }

            addManifestArtifact(tasks, scope.getVariantScope().getVariantData());
        }

    }

    /** Creates configuration action for the merge manifests task. */
    @NonNull
    protected TaskConfigAction<? extends ManifestProcessorTask> getMergeManifestConfig(
            @NonNull VariantOutputScope scope,
            @NonNull List<ManifestMerger2.Invoker.Feature> optionalFeatures) {
        return new MergeManifests.ConfigAction(scope, optionalFeatures);
    }

    /**
     * Adds the manifest artifact for the variant.
     *
     * This artifact is added if the publishNonDefault option is {@code true}.
     * See {@link VariantDependencies#compute variant dependencies evaluation} for more details
     */
    private void addManifestArtifact(
            @NonNull TaskFactory tasks,
            @NonNull  BaseVariantData<? extends BaseVariantOutputData> variantData) {
        if (variantData.getVariantDependency().getManifestConfiguration() != null) {
            ManifestProcessorTask mergeManifestsTask =
                    variantData.getOutputs().get(0).getScope().getManifestProcessorTask()
                    .get(tasks);
            project.getArtifacts().add(
                    variantData.getVariantDependency().getManifestConfiguration().getName(),
                    new ManifestPublishArtifact(
                            globalScope.getProjectBaseName(),
                            new FileSupplier() {
                                @NonNull
                                @Override
                                public Task getTask() {
                                    return mergeManifestsTask;
                                }

                                @Override
                                public File get() {
                                    return mergeManifestsTask.getManifestOutputFile();
                                }
                            }));
        }
    }

    public void createMergeLibManifestsTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {

        AndroidTask<ProcessManifest> processManifest = androidTasks.create(tasks,
                new ProcessManifest.ConfigAction(scope));

        processManifest.dependsOn(tasks, scope.getPrepareDependenciesTask());

        BaseVariantOutputData variantOutputData = scope.getVariantData().getOutputs().get(0);
        variantOutputData.getScope().setManifestProcessorTask(processManifest);
    }

    protected void createProcessTestManifestTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {

        AndroidTask<ProcessTestManifest> processTestManifestTask = androidTasks.create(tasks,
                new ProcessTestManifest.ConfigAction(scope));

        processTestManifestTask.dependsOn(tasks, scope.getPrepareDependenciesTask());

        BaseVariantOutputData variantOutputData = scope.getVariantData().getOutputs().get(0);
        variantOutputData.getScope().setManifestProcessorTask(processTestManifestTask);
    }

    public void createRenderscriptTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        scope.setRenderscriptCompileTask(
                androidTasks.create(tasks, new RenderscriptCompile.ConfigAction(scope)));

        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        GradleVariantConfiguration config = variantData.getVariantConfiguration();

        if (config.getRenderscriptSupportModeEnabled() && config.getRenderscriptTarget() >= 21) {
            Revision rev =  androidBuilder.getTargetInfo().getBuildTools().getRevision();
            if (rev.compareTo(MIN_REVISION_RS_COMPAT_64) < 0)
                androidBuilder.getErrorReporter().handleSyncError(
                        rev.toString(),
                        SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW,
                        "Renderscript support mode is not supported with renderscript target 21+ in BuildTools "
                                + rev.toString()
                                + '\n'
                                + "Please update to BuildTools "
                                + MIN_REVISION_RS_COMPAT_64.toString()
                                + " or above.");
        }

        // get single output for now.
        BaseVariantOutputData variantOutputData = variantData.getOutputs().get(0);

        scope.getRenderscriptCompileTask().dependsOn(tasks, scope.getPrepareDependenciesTask());
        if (config.getType().isForTesting()) {
            scope.getRenderscriptCompileTask().dependsOn(tasks,
                    variantOutputData.getScope().getManifestProcessorTask());
        } else {
            scope.getRenderscriptCompileTask().dependsOn(tasks, scope.getCheckManifestTask());
        }

        scope.getResourceGenTask().dependsOn(tasks, scope.getRenderscriptCompileTask());
        // only put this dependency if rs will generate Java code
        if (!config.getRenderscriptNdkModeEnabled()) {
            scope.getSourceGenTask().dependsOn(tasks, scope.getRenderscriptCompileTask());
        }

    }

    public AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        return createMergeResourcesTask(tasks, scope, true /*process9patch*/);
    }

    public AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            boolean process9patch) {
        return basicCreateMergeResourcesTask(
                tasks,
                scope,
                "merge",
                null /*outputLocation*/,
                true /*includeDependencies*/,
                process9patch);
    }

    public AndroidTask<MergeResources> basicCreateMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull String taskNamePrefix,
            @Nullable File outputLocation,
            final boolean includeDependencies,
            final boolean process9Patch) {
        AndroidTask<MergeResources> mergeResourcesTask = androidTasks.create(tasks,
                new MergeResources.ConfigAction(
                        scope,
                        taskNamePrefix,
                        outputLocation,
                        includeDependencies,
                        process9Patch));

        if (getIncrementalMode(scope.getVariantConfiguration()) != IncrementalMode.LOCAL_RES_ONLY) {
            mergeResourcesTask.dependsOn(tasks,
                    scope.getPrepareDependenciesTask(),
                    scope.getResourceGenTask());
        }
        scope.setMergeResourcesTask(mergeResourcesTask);
        scope.setResourceOutputDir(
                Objects.firstNonNull(outputLocation, scope.getDefaultMergeResourcesOutputDir()));
        scope.setMergeResourceOutputDir(outputLocation);
        return scope.getMergeResourcesTask();
    }

    public void createMergeAssetsTask(TaskFactory tasks, VariantScope scope) {
        AndroidTask<MergeSourceSetFolders> mergeAssetsTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeAssetConfigAction(scope));
        mergeAssetsTask.dependsOn(tasks,
                scope.getPrepareDependenciesTask().getName(),
                scope.getAssetGenTask());
        scope.setMergeAssetsTask(mergeAssetsTask);
    }

    public void createMergeJniLibFoldersTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        // merge the source folders together using the proper priority.
        AndroidTask<MergeSourceSetFolders> mergeJniLibFoldersTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeJniLibFoldersConfigAction(variantScope));
        mergeJniLibFoldersTask.dependsOn(tasks,
                variantScope.getPrepareDependenciesTask().getName(),
                variantScope.getAssetGenTask());
        variantScope.setMergeJniLibFoldersTask(mergeJniLibFoldersTask);

        // create the stream generated from this task
        variantScope.getTransformManager().addStream(OriginalStream.builder()
                .addContentType(ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.PROJECT)
                .setFolder(variantScope.getMergeNativeLibsOutputDir())
                .setDependency(mergeJniLibFoldersTask.getName())
                .build());

        // create a stream that contains the content of the local NDK build
        variantScope.getTransformManager().addStream(OriginalStream.builder()
                .addContentType(ExtendedContentType.NATIVE_LIBS)
                .addScope(Scope.PROJECT)
                .setFolders(Suppliers.ofInstance(variantScope.getNdkSoFolder()))
                .setDependency(getNdkBuildable(variantScope.getVariantData()))
                .build());

        // create a stream that contains the content of the local external native build
        if (variantScope.getExternalNativeJsonGenerator() != null) {
            variantScope.getTransformManager().addStream(OriginalStream.builder()
                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                    .addScope(Scope.PROJECT)
                    .setFolder(variantScope.getExternalNativeJsonGenerator().getSoFolder())
                    .setDependency(variantScope.getExternalNativeBuildTask().getName())
                    .build());
        }

        // create a stream containing the content of the renderscript compilation output
        // if support mode is enabled.
        if (variantScope.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
            variantScope.getTransformManager().addStream(OriginalStream.builder()
                    .addContentType(ExtendedContentType.NATIVE_LIBS)
                    .addScope(Scope.PROJECT)
                    .setFolders(() -> {
                        ImmutableList.Builder<File> builder = ImmutableList.builder();

                        if (variantScope.getRenderscriptLibOutputDir().isDirectory()) {
                            builder.add(variantScope.getRenderscriptLibOutputDir());
                        }

                        File rsLibs = variantScope.getGlobalScope().getAndroidBuilder()
                                .getSupportNativeLibFolder();
                        if (rsLibs != null && rsLibs.isDirectory()) {
                            builder.add(rsLibs);
                        }
                        if (variantScope.getVariantConfiguration()
                                .getRenderscriptSupportModeBlasEnabled()) {
                            File rsBlasLib = variantScope.getGlobalScope().getAndroidBuilder()
                                    .getSupportBlasLibFolder();
                            if (rsBlasLib != null && rsBlasLib.isDirectory()) {
                                builder.add(rsBlasLib);
                            } else {
                                Revision rev =  androidBuilder.getTargetInfo()
                                        .getBuildTools().getRevision();
                                androidBuilder.getErrorReporter().handleSyncError(
                                        rev.toString(),
                                        SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW,
                                        "Renderscript BLAS support mode is not supported in BuildTools "
                                                + rev.toString()
                                                + '\n'
                                                + "Please update to BuildTools 24.0.0"
                                                + " or above.");
                            }
                        }
                        return builder.build();
                    })
                    .setDependency(variantScope.getRenderscriptCompileTask().getName())
                .build());
        }

        // compute the scopes that need to be merged.
        Set<Scope> mergeScopes = getResMergingScopes(variantScope);
        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, ExtendedContentType.NATIVE_LIBS, "mergeJniLibs");
        variantScope.getTransformManager().addTransform(tasks, variantScope, mergeTransform);
    }

    public void createBuildConfigTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        AndroidTask<GenerateBuildConfig> generateBuildConfigTask =
                androidTasks.create(tasks, new GenerateBuildConfig.ConfigAction(scope));
        scope.setGenerateBuildConfigTask(generateBuildConfigTask);
        scope.getSourceGenTask().dependsOn(tasks, generateBuildConfigTask.getName());
        if (scope.getVariantConfiguration().getType().isForTesting()) {
            // in case of a test project, the manifest is generated so we need to depend
            // on its creation.

            // For test apps there should be a single output, so we get it.
            BaseVariantOutputData variantOutputData = scope.getVariantData().getOutputs().get(0);

            generateBuildConfigTask.dependsOn(
                    tasks, variantOutputData.getScope().getManifestProcessorTask());
        } else {
            generateBuildConfigTask.dependsOn(tasks, scope.getCheckManifestTask());
        }
    }

    public void createGenerateResValuesTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        AndroidTask<GenerateResValues> generateResValuesTask = androidTasks.create(
                tasks, new GenerateResValues.ConfigAction(scope));
        scope.getResourceGenTask().dependsOn(tasks, generateResValuesTask);
    }

    public void createApkProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        createProcessResTask(
                tasks,
                scope,
                new File(globalScope.getIntermediatesDir(),
                        "symbols/" + scope.getVariantData().getVariantConfiguration().getDirName()),
                true);
    }

    public void createProcessResTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @Nullable File symbolLocation,
            boolean generateResourcePackage) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        variantData.calculateFilters(scope.getGlobalScope().getExtension().getSplits());
        boolean useAaptToGenerateLegacyMultidexMainDexProguardRules =
                useAaptToGenerateLegacyMultidexMainDexProguardRules(scope);

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (BaseVariantOutputData vod : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = vod.getScope();

            variantOutputScope.setProcessResourcesTask(androidTasks.create(tasks,
                    new ProcessAndroidResources.ConfigAction(variantOutputScope, symbolLocation,
                            generateResourcePackage,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules)));

            // always depend on merge res,
            variantOutputScope.getProcessResourcesTask().dependsOn(tasks,
                    scope.getMergeResourcesTask());
            if (scope.getDataBindingProcessLayoutsTask() != null) {
                variantOutputScope.getProcessResourcesTask().dependsOn(tasks,
                        scope.getDataBindingProcessLayoutsTask().getName());
            }
            if (getIncrementalMode(scope.getVariantConfiguration())
                    != IncrementalMode.LOCAL_RES_ONLY) {
                variantOutputScope.getProcessResourcesTask().dependsOn(tasks,
                        variantOutputScope.getManifestProcessorTask(),
                        scope.getMergeAssetsTask());
            }

            if (vod.getMainOutputFile().getFilter(DENSITY) == null) {
                scope.setGenerateRClassTask(variantOutputScope.getProcessResourcesTask());
                scope.getSourceGenTask().optionalDependsOn(
                        tasks,
                        variantOutputScope.getProcessResourcesTask());
            }

        }
    }

    private boolean useAaptToGenerateLegacyMultidexMainDexProguardRules(
            @NonNull VariantScope scope) {
        return isLegacyMultidexMode(scope) &&
                androidBuilder.getTargetInfo().getBuildTools().getRevision()
                        .compareTo(Aapt.VERSION_FOR_MAIN_DEX_LIST) >= 0;
    }

    /**
     * Creates the split resources packages task if necessary. AAPT will produce split packages for
     * all --split provided parameters. These split packages should be signed and moved unchanged to
     * the APK build output directory.
     */
    @NonNull
    public AndroidTask<PackageSplitRes> createSplitResourcesTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();

        checkState(variantData.getSplitHandlingPolicy().equals(
                        SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "Can only create split resources tasks for pure splits.");

        List<? extends BaseVariantOutputData> outputs = variantData.getOutputs();
        final BaseVariantOutputData variantOutputData = outputs.get(0);
        if (outputs.size() != 1) {
            throw new RuntimeException(
                    "In release 21 and later, there can be only one main APK, " +
                            "found " + outputs.size());
        }

        VariantOutputScope variantOutputScope = variantOutputData.getScope();
        AndroidTask<PackageSplitRes> packageSplitRes =
                androidTasks.create(tasks, new PackageSplitRes.ConfigAction(scope));
        packageSplitRes.dependsOn(tasks,
                variantOutputScope.getProcessResourcesTask().getName());
        return packageSplitRes;
    }

    @Nullable
    public AndroidTask<PackageSplitAbi> createSplitAbiTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope scope) {
        ApplicationVariantData variantData = (ApplicationVariantData) scope.getVariantData();

        checkState(variantData.getSplitHandlingPolicy().equals(
                SplitHandlingPolicy.RELEASE_21_AND_AFTER_POLICY),
                "split ABI tasks are only compatible with pure splits.");

        Set<String> filters = AbiSplitOptions.getAbiFilters(
                getExtension().getSplits().getAbiFilters());
        if (filters.isEmpty()) {
            return null;
        }

        List<ApkVariantOutputData> outputs = variantData.getOutputs();
        if (outputs.size() != 1) {
            throw new RuntimeException(
                    "In release 21 and later, there can be only one main APK, " +
                            "found " + outputs.size());
        }

        BaseVariantOutputData variantOutputData = outputs.get(0);

        // first create the split APK resources.
        AndroidTask<GenerateSplitAbiRes> generateSplitAbiRes =
                androidTasks.create(tasks, new GenerateSplitAbiRes.ConfigAction(scope));
        generateSplitAbiRes.dependsOn(tasks,
                variantOutputData.getScope().getProcessResourcesTask().getName());

        // then package those resources with the appropriate JNI libraries.
        AndroidTask<PackageSplitAbi> packageSplitAbiTask =
                androidTasks.create(tasks, new PackageSplitAbi.ConfigAction(scope));

        packageSplitAbiTask.dependsOn(tasks, generateSplitAbiRes);
        packageSplitAbiTask.dependsOn(tasks, scope.getNdkBuildable());
        if (scope.getExternalNativeBuildTask() != null) {
            packageSplitAbiTask.dependsOn(tasks, scope.getExternalNativeBuildTask());
        }

        // set up dependency on the jni merger.
        for (TransformStream stream : scope.getTransformManager().getStreams(
                StreamFilter.NATIVE_LIBS)) {
            packageSplitAbiTask.dependsOn(tasks, stream.getDependencies());
        }
        return packageSplitAbiTask;
    }

    public void createSplitTasks(@NonNull TaskFactory tasks, @NonNull final VariantScope scope) {
        AndroidTask<PackageSplitRes> packageSplitResourcesTask =
                createSplitResourcesTasks(tasks, scope);
        final AndroidTask<PackageSplitAbi> packageSplitAbiTask = createSplitAbiTasks(tasks, scope);

        AndroidTask<SplitZipAlign> zipAlign =
                androidTasks.create(tasks, new SplitZipAlign.ConfigAction(scope));
        //noinspection VariableNotUsedInsideIf - only need to check if task exist.
        if (packageSplitAbiTask != null) {
            zipAlign.configure(tasks,
                    task -> ((SplitZipAlign)task).getAbiInputFiles().addAll(
                            scope.getPackageSplitAbiOutputFiles()));
        }
        zipAlign.dependsOn(tasks, packageSplitResourcesTask);
        zipAlign.optionalDependsOn(tasks, packageSplitAbiTask);

        BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        VariantOutputScope outputScope = variantData.getOutputs().get(0).getScope();
        outputScope.setSplitZipAlignTask(zipAlign);
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     * @param variantScope the scope of the variant being processed.
     * @return the list of scopes for which to merge the java resources.
     */
    @NonNull
    protected abstract Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope);

    /**
     * Creates the java resources processing tasks.
     *
     * The java processing will happen in two steps:
     * <ul>
     * <li>{@link Sync} task configured with {@link ProcessJavaResConfigAction} will sync all source
     * folders into a single folder identified by
     * {@link VariantScope#getSourceFoldersJavaResDestinationDir()}</li>
     * <li>{@link MergeJavaResourcesTransform} will take the output of this merge plus the
     * dependencies and will create a single merge with the {@link PackagingOptions} settings
     * applied.</li>
     * </ul>
     *
     * @param tasks tasks factory to create tasks.
     * @param variantScope the variant scope we are operating under.
     */
    public void createProcessJavaResTasks(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        TransformManager transformManager = variantScope.getTransformManager();

        // now copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        AndroidTask<Sync> processJavaResourcesTask =
                androidTasks.create(tasks, new ProcessJavaResConfigAction(variantScope));
        variantScope.setProcessJavaResourcesTask(processJavaResourcesTask);

        // create the stream generated from this task
        variantScope.getTransformManager().addStream(OriginalStream.builder()
                .addContentType(DefaultContentType.RESOURCES)
                .addScope(Scope.PROJECT)
                .setFolder(variantScope.getSourceFoldersJavaResDestinationDir())
                .setDependency(processJavaResourcesTask.getName())
                .build());

        // compute the scopes that need to be merged.
        Set<Scope> mergeScopes = getResMergingScopes(variantScope);

        // Create the merge transform
        MergeJavaResourcesTransform mergeTransform = new MergeJavaResourcesTransform(
                variantScope.getGlobalScope().getExtension().getPackagingOptions(),
                mergeScopes, DefaultContentType.RESOURCES, "mergeJavaRes");

        variantScope.setMergeJavaResourcesTask(
                transformManager.addTransform(tasks, variantScope, mergeTransform));
    }

    public void createAidlTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        scope.setAidlCompileTask(androidTasks.create(tasks, new AidlCompile.ConfigAction(scope)));
        scope.getSourceGenTask().dependsOn(tasks, scope.getAidlCompileTask());
        scope.getAidlCompileTask().dependsOn(tasks, scope.getPrepareDependenciesTask());
    }

    public void createShaderTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        // merge the shader folders together using the proper priority.
        AndroidTask<MergeSourceSetFolders> mergeShadersTask = androidTasks.create(tasks,
                new MergeSourceSetFolders.MergeShaderSourceFoldersConfigAction(scope));
        // TODO do we support non compiled shaders in aars?
        //mergeShadersTask.dependsOn(tasks, scope.getVariantData().prepareDependenciesTask);

        // compile the shaders
        AndroidTask<ShaderCompile> shaderCompileTask = androidTasks.create(
                tasks, new ShaderCompile.ConfigAction(scope));
        scope.setShaderCompileTask(shaderCompileTask);
        shaderCompileTask.dependsOn(tasks, mergeShadersTask);

        scope.getAssetGenTask().dependsOn(tasks, shaderCompileTask);
    }

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    public AndroidTask<? extends JavaCompile> createJavacTask(
            @NonNull final TaskFactory tasks,
            @NonNull final VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        AndroidTask<IncrementalSafeguard> javacIncrementalSafeguard = androidTasks.create(tasks,
                new IncrementalSafeguard.ConfigAction(scope));

        final AndroidTask<? extends JavaCompile> javacTask = androidTasks.create(tasks,
                new JavaCompileConfigAction(scope));
        scope.setJavacTask(javacTask);
        javacTask.dependsOn(tasks, javacIncrementalSafeguard);

        setupCompileTaskDependencies(tasks, scope, javacTask);

        // Create jar task for uses by external modules.
        if (variantData.getVariantDependency().getClassesConfiguration() != null) {
            AndroidTask<Jar> packageJarArtifact =
                    androidTasks.create(tasks, new PackageJarArtifactConfigAction(scope));
            packageJarArtifact.dependsOn(tasks, javacTask);
        }

        return javacTask;
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     * This should not be called for classes that will also be compiled from source by jack.
     */
    public static void addJavacClassesStream(VariantScope scope) {
        checkNotNull(scope.getJavacTask());
        // create the output stream from this task
        scope.getTransformManager().addStream(OriginalStream.builder()
                .addContentType(DefaultContentType.CLASSES)
                .addScope(Scope.PROJECT)
                .setFolder(scope.getJavaOutputDir())
                .setDependency(scope.getJavacTask().getName())
                .build());
    }

    private void setupCompileTaskDependencies(@NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            AndroidTask<?> compileTask) {
        IncrementalMode incrementalMode = getIncrementalMode(scope.getVariantConfiguration());

        if (incrementalMode == IncrementalMode.LOCAL_RES_ONLY) {
            // in this case only depend on the R class. We want to ignore the other
            // source generating classes like RS, aidl, etc...
            compileTask.optionalDependsOn(tasks, scope.getGenerateRClassTask());
        } else if (incrementalMode != IncrementalMode.LOCAL_JAVA_ONLY) {
            compileTask.optionalDependsOn(tasks, scope.getSourceGenTask());
            compileTask.dependsOn(tasks, scope.getPrepareDependenciesTask());
            // TODO - dependency information for the compile classpath is being lost.
            // Add a temporary approximation
            compileTask.dependsOn(tasks,
                    scope.getVariantData().getVariantDependency().getCompileConfiguration()
                            .getBuildDependencies());
        }
    }

    /**
     * Makes the given task the one used by top-level "compile" task.
     */
    public static void setJavaCompilerTask(
            @NonNull AndroidTask<? extends Task> javaCompilerTask,
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope) {
        scope.getCompileTask().dependsOn(tasks, javaCompilerTask);
        scope.setJavaCompilerTask(javaCompilerTask);

        // TODO: Get rid of it once we stop keeping tasks in variant data.
        //noinspection VariableNotUsedInsideIf
        if (scope.getVariantData().javacTask != null) {
            // This is not the experimental plugin, let's update variant data, so Variants API
            // keeps working.
            scope.getVariantData().javaCompilerTask = tasks.named(javaCompilerTask.getName());
        }
    }

    public void createGenerateMicroApkDataTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope scope,
            @NonNull Configuration config) {
        AndroidTask<GenerateApkDataTask> generateMicroApkTask = androidTasks.create(tasks,
                new GenerateApkDataTask.ConfigAction(scope, config));
        scope.setMicroApkTask(generateMicroApkTask);
        generateMicroApkTask.dependsOn(tasks, config);

        // the merge res task will need to run after this one.
        scope.getResourceGenTask().dependsOn(tasks, generateMicroApkTask);
    }

    public void createExternalNativeBuildJsonGenerators(TaskFactory tasks, @NonNull VariantScope scope) {

        CoreExternalNativeBuild externalNativeBuild = extension.getExternalNativeBuild();
        ExternalNativeBuildTaskUtils.ExternalNativeBuildProjectPathResolution pathResolution =
                ExternalNativeBuildTaskUtils.getProjectPath(externalNativeBuild);
        if (pathResolution.errorText != null) {
            androidBuilder.getErrorReporter().handleSyncError(
                    scope.getVariantConfiguration().getFullName(),
                    SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    pathResolution.errorText);
            return;
        }

        if (pathResolution.makeFile == null) {
            // No project
            return;
        }

        // Disable instant run when external native build is enabled.
        scope.getVariantConfiguration().setEnableInstantRunOverride(false);

        scope.setExternalNativeJsonGenerator(ExternalNativeJsonGenerator.create(
                project.getProjectDir(),
                pathResolution.buildSystem,
                pathResolution.makeFile,
                androidBuilder,
                sdkHandler,
                scope
        ));
    }

    public void createExternalNativeBuildTasks(TaskFactory tasks, @NonNull VariantScope scope) {
        ExternalNativeJsonGenerator generator = scope.getExternalNativeJsonGenerator();
        if (generator == null) {
            return;
        }

        AndroidTask<?> generateTask = androidTasks.create(tasks,
                ExternalNativeBuildJsonTask.createTaskConfigAction(
                        generator, scope));

        generateTask.dependsOn(tasks, scope.getPreBuildTask());

        AndroidTask<ExternalNativeBuildTask> buildTask = androidTasks.create(
                tasks,
                new ExternalNativeBuildTask.ConfigAction(generator, scope, androidBuilder));

        buildTask.dependsOn(tasks, generateTask);
        scope.setExternalNativeBuildTask(buildTask);
        scope.getCompileTask().dependsOn(tasks, buildTask);
    }

    public void createNdkTasks(@NonNull VariantScope scope) {
        if (ExternalNativeBuildTaskUtils.isExternalNativeBuildEnabled(
                extension.getExternalNativeBuild())) {
            return;
        }

        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        NdkCompile ndkCompile = project.getTasks().create(
                scope.getTaskName("compile", "Ndk"),
                NdkCompile.class);

        ndkCompile.dependsOn(scope.getPreBuildTask().getName());

        ndkCompile.setAndroidBuilder(androidBuilder);
        ndkCompile.setVariantName(variantData.getName());
        ndkCompile.setNdkDirectory(sdkHandler.getNdkFolder());
        ndkCompile.setForTesting(variantData.getType().isForTesting());
        variantData.ndkCompileTask = ndkCompile;
        variantData.compileTask.dependsOn(variantData.ndkCompileTask);

        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        if (Boolean.TRUE.equals(variantConfig.getMergedFlavor().getRenderscriptNdkModeEnabled())) {
            ndkCompile.setNdkRenderScriptMode(true);
            ndkCompile.dependsOn(variantData.renderscriptCompileTask);
        } else {
            ndkCompile.setNdkRenderScriptMode(false);
        }

        ConventionMappingHelper.map(ndkCompile, "sourceFolders", (Callable<List<File>>) () -> {
            List<File> sourceList = variantConfig.getJniSourceList();
            if (Boolean.TRUE.equals(
                    variantConfig.getMergedFlavor().getRenderscriptNdkModeEnabled())) {
                sourceList.add(variantData.renderscriptCompileTask.getSourceOutputDir());
            }

            return sourceList;
        });

        ndkCompile.setGeneratedMakefile(new File(scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/Android.mk"));

        ConventionMappingHelper.map(ndkCompile, "ndkConfig",
                (Callable<CoreNdkOptions>) variantConfig::getNdkConfig);

        ConventionMappingHelper.map(ndkCompile, "debuggable",
                (Callable<Boolean>) () -> variantConfig.getBuildType().isJniDebuggable());

        ndkCompile.setObjFolder(new File(scope.getGlobalScope().getIntermediatesDir(),
                "ndk/" + variantData.getVariantConfiguration().getDirName() + "/obj"));

        Collection<File> ndkSoFolder = scope.getNdkSoFolder();
        if (ndkSoFolder != null && !ndkSoFolder.isEmpty()) {
            ndkCompile.setSoFolder(ndkSoFolder.iterator().next());
        }
    }

    /**
     * Creates the tasks to build unit tests.
     */
    public void createUnitTestVariantTasks(
            @NonNull TaskFactory tasks,
            @NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();
        BaseVariantData testedVariantData = variantScope.getTestedVariantData();
        checkState(testedVariantData != null);

        createPreBuildTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        createProcessJavaResTasks(tasks, variantScope);
        createCompileAnchorTask(tasks, variantScope);

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add an
        // explicit dependency on resource copying tasks.
        variantScope.getCompileTask().dependsOn(
                tasks,
                variantScope.getProcessJavaResourcesTask(),
                testedVariantData.getScope().getProcessJavaResourcesTask());

        AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, tasks, variantScope);
        javacTask.dependsOn(tasks, testedVariantData.getScope().getJavacTask());

        createRunUnitTestTask(tasks, variantScope);

        variantScope.getAssembleTask().dependsOn(tasks, createMockableJar);
        // This hides the assemble unit test task from the task list.
        variantScope.getAssembleTask().configure(tasks, task -> task.setGroup(null));
    }

    /**
     * Creates the tasks to build android tests.
     */
    public void createAndroidTestVariantTasks(@NonNull TaskFactory tasks,
            @NonNull TestVariantData variantData) {
        VariantScope variantScope = variantData.getScope();

        // get single output for now (though this may always be the case for tests).
        final BaseVariantOutputData variantOutputData = variantData.getOutputs().get(0);

        final BaseVariantData<BaseVariantOutputData> testedVariantData =
                (BaseVariantData<BaseVariantOutputData>) variantData.getTestedVariantData();
        final BaseVariantOutputData testedVariantOutputData =
                testedVariantData.getOutputs().get(0);

        createAnchorTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        // Add a task to process the manifest
        createProcessTestManifestTask(tasks, variantScope);

        // Add a task to create the res values
        createGenerateResValuesTask(tasks, variantScope);

        // Add a task to compile renderscript files.
        createRenderscriptTask(tasks, variantScope);

        // Add a task to merge the resource folders
        createMergeResourcesTask(tasks, variantScope);

        // Add a task to merge the assets folders
        createMergeAssetsTask(tasks, variantScope);

        if (variantData.getTestedVariantData().getVariantConfiguration().getType().equals(
                VariantType.LIBRARY)) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariantOutputData.getScope().getAssembleTask() != null) {
                String bundle =
                        testedVariantOutputData.getScope().getVariantScope().getTaskName("bundle");
                variantOutputData.getScope().getManifestProcessorTask().dependsOn(tasks, bundle);
                variantScope.getMergeResourcesTask().dependsOn(tasks, bundle);
            }
        }

        // Add a task to create the BuildConfig class
        createBuildConfigTask(tasks, variantScope);

        // Add a task to generate resource source files
        createApkProcessResTask(tasks, variantScope);

        // process java resources
        createProcessJavaResTasks(tasks, variantScope);

        createAidlTask(tasks, variantScope);

        createShaderTask(tasks, variantScope);

        // Add NDK tasks
        if (!isComponentModelPlugin) {
            createNdkTasks(variantScope);
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(tasks, variantScope);

        // Add a task to compile the test application
        CoreJackOptions jackOptions = variantData.getVariantConfiguration().getJackOptions();
        if (jackOptions.isEnabled()) {
            AndroidTask<TransformTask> jackTask =
                    createJackTask(tasks, variantScope, true /*compileJavaSources*/);
            setJavaCompilerTask(jackTask, tasks, variantScope);
        } else {
            AndroidTask<? extends JavaCompile> javacTask = createJavacTask(tasks, variantScope);
            addJavacClassesStream(variantScope);
            setJavaCompilerTask(javacTask, tasks, variantScope);
            createPostCompilationTasks(tasks, variantScope);
        }
        checkNotNull(variantScope.getJavaCompilerTask());
        variantScope.getJavaCompilerTask().dependsOn(
                tasks,
                testedVariantData.getScope().getJavaCompilerTask());

        // Add data binding tasks if enabled
        if (extension.getDataBinding().isEnabled()) {
            createDataBindingTasks(tasks, variantScope);
        }

        createPackagingTask(tasks, variantScope, false /*publishApk*/,
                null /* buildInfoGeneratorTask */);

        tasks.named(ASSEMBLE_ANDROID_TEST, assembleTest ->
                assembleTest.dependsOn(variantOutputData.getScope().getAssembleTask().getName()));

        createConnectedTestForVariant(tasks, variantScope);
    }


    protected enum IncrementalMode {
        NONE, FULL, LOCAL_JAVA_ONLY, LOCAL_RES_ONLY
    }

    /**
     * Returns the incremental mode for this variant.
     * @param config the variant's configuration
     * @return the {@link IncrementalMode} for this variant.
     */
    protected IncrementalMode getIncrementalMode(@NonNull GradleVariantConfiguration config) {
        if (config.isInstantRunSupported()
                && targetDeviceSupportsInstantRun(config, project)
                && globalScope.isActive(OptionalCompilationStep.INSTANT_DEV)) {
            if (isComponentModelPlugin) {
                return IncrementalMode.FULL;
            }

            // while both LOCAL_RES and LOCAL_JAVA could be active, LOCAL_RES is higher priority.
            if (globalScope.isActive(OptionalCompilationStep.LOCAL_RES_ONLY)) {
                return IncrementalMode.LOCAL_RES_ONLY;
            }

            if (globalScope.isActive(OptionalCompilationStep.LOCAL_JAVA_ONLY)) {
                return IncrementalMode.LOCAL_JAVA_ONLY;
            }

            return IncrementalMode.FULL;
        }

        return IncrementalMode.NONE;
    }

    private static boolean targetDeviceSupportsInstantRun(
            @NonNull GradleVariantConfiguration config,
            @NonNull Project project) {
        if (config.isLegacyMultiDexMode()) {
            // We don't support legacy multi-dex on Dalvik.
            return AndroidGradleOptions.getTargetFeatureLevel(project) >=
                    AndroidVersion.ART_RUNTIME.getFeatureLevel();
        }

        return true;
    }

    /**
     * Is the given variant relevant for lint?
     */
    private static boolean isLintVariant(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> baseVariantData) {
        // Only create lint targets for variants like debug and release, not debugTest
        VariantConfiguration config = baseVariantData.getVariantConfiguration();
        return !config.getType().isForTesting();
    }

    /**
     * Add tasks for running lint on individual variants. We've already added a
     * lint task earlier which runs on all variants.
     */
    public void createLintTasks(TaskFactory tasks, final VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> baseVariantData =
                scope.getVariantData();
        if (!isLintVariant(baseVariantData)) {
            return;
        }

        // wire the main lint task dependency.
        tasks.named(LINT, lint -> lint.dependsOn(scope.getJavacTask().getName()));

        AndroidTask<Lint> variantLintCheck = androidTasks.create(
                tasks, new Lint.ConfigAction(scope));
        variantLintCheck.dependsOn(tasks, LINT_COMPILE, scope.getJavacTask());
    }

    private void createLintVitalTask(
            @NonNull TaskFactory tasks,
            @NonNull ApkVariantData variantData) {
        checkState(getExtension().getLintOptions().isCheckReleaseBuilds());
        // TODO: re-enable with Jack when possible
        if (!variantData.getVariantConfiguration().getBuildType().isDebuggable() &&
                !variantData.getVariantConfiguration().getJackOptions().isEnabled()) {
            final AndroidTask<Lint> lintReleaseCheck = androidTasks.create(
                    tasks,
                    new Lint.VitalConfigAction(variantData.getScope()));
            lintReleaseCheck.optionalDependsOn(tasks, variantData.javacTask);

            variantData.getScope().getAssembleTask().dependsOn(tasks, lintReleaseCheck);

            // If lint is being run, we do not need to run lint vital.
            // TODO: Find a better way to do this.
            project.getGradle().getTaskGraph().whenReady(new Closure<Void>(this, this) {
                public void doCall(TaskExecutionGraph taskGraph) {
                    if (taskGraph.hasTask(LINT)) {
                        project.getTasks().getByName(lintReleaseCheck.getName()).setEnabled(false);
                    }
                }
            });
        }
    }

    private void createRunUnitTestTask(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final AndroidTask<AndroidUnitTest> runTestsTask =
                androidTasks.create(tasks, new AndroidUnitTest.ConfigAction(variantScope));
        runTestsTask.dependsOn(tasks, variantScope.getAssembleTask());

        tasks.named(JavaPlugin.TEST_TASK_NAME, test -> test.dependsOn(runTestsTask.getName()));
    }

    public void createTopLevelTestTasks(final TaskFactory tasks, boolean hasFlavors) {
        createMockableJarTask(tasks);

        final List<String> reportTasks = Lists.newArrayListWithExpectedSize(2);

        List<DeviceProvider> providers = getExtension().getDeviceProviders();

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // DefaultTask.

        AndroidTask<? extends DefaultTask> connectedAndroidTestTask;
        if (hasFlavors) {
            connectedAndroidTestTask = androidTasks.create(tasks,
                    new AndroidReportTask.ConfigAction(
                            globalScope,
                            AndroidReportTask.ConfigAction.TaskKind.CONNECTED));
            reportTasks.add(connectedAndroidTestTask.getName());
        } else {
            connectedAndroidTestTask = androidTasks.create(tasks,
                    CONNECTED_ANDROID_TEST,
                    connectedTask -> {
                        connectedTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                        connectedTask.setDescription("Installs and runs instrumentation tests "
                                + "for all flavors on connected devices.");
            });
        }

        tasks.named(CONNECTED_CHECK, check -> check.dependsOn(connectedAndroidTestTask.getName()));

        AndroidTask<? extends DefaultTask> deviceAndroidTestTask;
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size() > 1 || hasFlavors) {
            deviceAndroidTestTask = androidTasks.create(tasks,
                    new AndroidReportTask.ConfigAction(
                            globalScope,
                            AndroidReportTask.ConfigAction.TaskKind.DEVICE_PROVIDER));
            reportTasks.add(deviceAndroidTestTask.getName());
        } else {
            deviceAndroidTestTask = androidTasks.create(tasks,
                    DEVICE_ANDROID_TEST,
                    providerTask -> {
                        providerTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                        providerTask.setDescription("Installs and runs instrumentation tests "
                                + "using all Device Providers.");
                    });
        }

        tasks.named(DEVICE_CHECK, check -> check.dependsOn(deviceAndroidTestTask.getName()));

        // Create top level unit test tasks.

        androidTasks.create(tasks, JavaPlugin.TEST_TASK_NAME, unitTestTask -> {
            unitTestTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            unitTestTask.setDescription("Run unit tests for all variants.");
        });
        tasks.named(JavaBasePlugin.CHECK_TASK_NAME,
                check -> check.dependsOn(JavaPlugin.TEST_TASK_NAME));

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (!reportTasks.isEmpty() && project.getGradle().getStartParameter()
                .isContinueOnFailure()) {
            project.getGradle().getTaskGraph().whenReady(new Closure<Void>(this, this) {
                public void doCall(TaskExecutionGraph taskGraph) {
                    for (String reportTask : reportTasks) {
                        if (taskGraph.hasTask(reportTask)) {
                            tasks.named(reportTask, new Action<Task>() {
                                @Override
                                public void execute(Task task) {
                                    ((AndroidReportTask) task).setWillRun();
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    protected void createConnectedTestForVariant(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {
        final BaseVariantData<? extends BaseVariantOutputData> baseVariantData =
                variantScope.getTestedVariantData();
        final TestVariantData testVariantData = (TestVariantData) variantScope.getVariantData();

        // get single output for now
        final BaseVariantOutputData variantOutputData = baseVariantData.getOutputs().get(0);
        final BaseVariantOutputData testVariantOutputData = testVariantData.getOutputs().get(0);

        TestDataImpl testData = new TestDataImpl(testVariantData);
        testData.setExtraInstrumentationTestRunnerArgs(
                AndroidGradleOptions.getExtraInstrumentationTestRunnerArgs(project));

        // create the check tasks for this test
        // first the connected one.
        ImmutableList<AndroidTask<DefaultTask>> artifactsTasks = ImmutableList.of(
                testVariantData.getOutputs().get(0).getScope().getAssembleTask(),
                baseVariantData.getScope().getAssembleTask());

        final AndroidTask<DeviceProviderInstrumentTestTask> connectedTask = androidTasks.create(
                tasks,
                new DeviceProviderInstrumentTestTask.ConfigAction(
                        testVariantData.getScope(),
                        new ConnectedDeviceProvider(sdkHandler.getSdkInfo().getAdb(),
                                globalScope.getExtension().getAdbOptions().getTimeOutInMs(),
                                new LoggerWrapper(logger)), testData));

        connectedTask.dependsOn(tasks, artifactsTasks.toArray());

        tasks.named(CONNECTED_ANDROID_TEST,
                connectedAndroidTest -> connectedAndroidTest.dependsOn(connectedTask.getName()));

        if (baseVariantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            final AndroidTask reportTask;
            if (baseVariantData.getVariantConfiguration().getJackOptions().isEnabled()) {
                reportTask = androidTasks.create(
                        tasks,
                        new JackJacocoReportTask.ConfigAction(variantScope));
            } else {
                reportTask = androidTasks.create(
                        tasks,
                        new JacocoReportTask.ConfigAction(variantScope));
            }
            reportTask.dependsOn(tasks, connectedTask.getName());

            variantScope.setCoverageReportTask(reportTask);
            baseVariantData.getScope().getCoverageReportTask().dependsOn(tasks, reportTask);

            tasks.named(CONNECTED_ANDROID_TEST,
                    connectedAndroidTest -> connectedAndroidTest.dependsOn(reportTask.getName()));
        }

        List<DeviceProvider> providers = getExtension().getDeviceProviders();

        boolean hasFlavors = baseVariantData.getVariantConfiguration().hasFlavors();

        // now the providers.
        for (DeviceProvider deviceProvider : providers) {

            final AndroidTask<DeviceProviderInstrumentTestTask> providerTask = androidTasks
                    .create(tasks, new DeviceProviderInstrumentTestTask.ConfigAction(
                            testVariantData.getScope(), deviceProvider, testData));

            providerTask.dependsOn(tasks, artifactsTasks.toArray());
            tasks.named(DEVICE_ANDROID_TEST,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(providerTask.getName()));
        }

        // now the test servers
        List<TestServer> servers = getExtension().getTestServers();
        for (final TestServer testServer : servers) {
            final AndroidTask<TestServerTask> serverTask = androidTasks.create(
                    tasks,
                    new TestServerTaskConfigAction(variantScope, testServer));
            serverTask.dependsOn(
                    tasks,
                    testVariantOutputData.getScope().getAssembleTask(),
                    variantOutputData.getScope().getAssembleTask());

            tasks.named(DEVICE_CHECK,
                    deviceAndroidTest -> deviceAndroidTest.dependsOn(serverTask.getName()));
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps like
     * proguard and jacoco
     *
     */
    public void createPostCompilationTasks(
            @NonNull TaskFactory tasks,
            @NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getJavacTask());

        variantScope.getInstantRunBuildContext().setInstantRunMode(
                getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE);

        final BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled = config.getBuildType().isTestCoverageEnabled() &&
                !config.getType().isForTesting() &&
                getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE;
        if (isTestCoverageEnabled) {
            createJacocoTransform(tasks, variantScope);
        }

        boolean isMinifyEnabled = isMinifyEnabled(variantScope);
        boolean isMultiDexEnabled = config.isMultiDexEnabled();
        // Switch to native multidex if possible when using instant run.
        boolean isLegacyMultiDexMode = isLegacyMultidexMode(variantScope);

        AndroidConfig extension = variantScope.getGlobalScope().getExtension();

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size() ; i < count ; i++) {
            Transform transform = customTransforms.get(i);
            AndroidTask<TransformTask> task = transformManager
                    .addTransform(tasks, variantScope, transform);
            // task could be null if the transform is invalid.
            if (task != null) {
                List<Object> deps = customTransformsDependencies.get(i);
                if (!deps.isEmpty()) {
                    task.dependsOn(tasks, deps);
                }

                // if the task is a no-op then we make assemble task depend on it.
                if (transform.getScopes().isEmpty()) {
                    variantScope.getAssembleTask().dependsOn(tasks, task);
                }

            }
        }

        // ----- Minify next -----

        if (isMinifyEnabled) {
            boolean outputToJarFile = isMultiDexEnabled && isLegacyMultiDexMode;
            createMinifyTransform(tasks, variantScope, outputToJarFile);
        }

        // ----- 10x support

        AndroidTask<PreColdSwapTask> preColdSwapTask = null;
        if (variantScope.getInstantRunBuildContext().isInInstantRunMode()) {

            variantScope.getInstantRunBuildContext().setInstantRunMode(true);

            AndroidTask<DefaultTask> allActionsAnchorTask =
                    createInstantRunAllActionsTasks(tasks, variantScope);
            assert variantScope.getInstantRunTaskManager() != null;
            preColdSwapTask = variantScope.getInstantRunTaskManager()
                    .createPreColdswapTask(project);
            preColdSwapTask.dependsOn(tasks, allActionsAnchorTask);

            // when dealing with platforms that can handle multi dexes natively, automatically
            // turn on multi dexing so shards are packaged as individual dex files.
            if (InstantRunPatchingPolicy.PRE_LOLLIPOP !=
                    variantScope.getInstantRunBuildContext().getPatchingPolicy()) {
                isMultiDexEnabled = true;
                // force pre-dexing to be true as we rely on individual slices to be packaged
                // separately.
                extension.getDexOptions().setPreDexLibraries(true);
                variantScope.getInstantRunTaskManager().createSlicerTask();
            }

            extension.getDexOptions().setJumboMode(true);
        }
        // ----- Multi-Dex support

        AndroidTask<TransformTask> multiDexClassListTask = null;
        // non Library test are running as native multi-dex
        if (isMultiDexEnabled && isLegacyMultiDexMode) {
            if (!variantData.getVariantConfiguration().getBuildType().isUseProguard()) {
                throw new IllegalStateException(
                        "Build-in class shrinker and multidex are not supported yet.");
            }

            // ----------
            // create a transform to jar the inputs into a single jar.
            if (!isMinifyEnabled) {
                // merge the classes only, no need to package the resources since they are
                // not used during the computation.
                JarMergingTransform jarMergingTransform = new JarMergingTransform(
                        TransformManager.SCOPE_FULL_PROJECT);
                variantScope.addColdSwapBuildTask(
                        transformManager.addTransform(tasks, variantScope, jarMergingTransform));
            }

            // ----------
            // Create a task to collect the list of manifest entry points which are
            // needed in the primary dex.
            // This is not needed for AAPT >= AaptV1.VERSION_FOR_MAIN_DEX_LIST,
            // where the '-D manifestKeepListProguardFile' option is used instead.
            AndroidTask<CreateManifestKeepList> manifestKeepListTask = null;
            if (!useAaptToGenerateLegacyMultidexMainDexProguardRules(variantScope)) {
                manifestKeepListTask = androidTasks.create(tasks,
                        new CreateManifestKeepList.ConfigAction(variantScope));
                manifestKeepListTask.dependsOn(tasks,
                        variantData.getOutputs().get(0).getScope().getManifestProcessorTask());
                variantScope.addColdSwapBuildTask(manifestKeepListTask);
            }
            // ---------
            // create the transform that's going to take the code and the proguard keep list
            // from above and compute the main class list.
            MultiDexTransform multiDexTransform = new MultiDexTransform(
                    variantScope,
                    null);
            multiDexClassListTask = transformManager.addTransform(
                    tasks, variantScope, multiDexTransform);
            multiDexClassListTask.optionalDependsOn(tasks, manifestKeepListTask);
            variantScope.addColdSwapBuildTask(multiDexClassListTask);
        }
        // create dex transform
        DefaultDexOptions dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());

        if (variantData.getType().isForTesting()) {
            // Don't use custom dx flags when compiling the test APK. They can break the test APK,
            // like --minimal-main-dex.
            dexOptions.setAdditionalParameters(ImmutableList.of());
        }

        DexTransform dexTransform = new DexTransform(
                dexOptions,
                config.getBuildType().isDebuggable(),
                isMultiDexEnabled,
                isMultiDexEnabled && isLegacyMultiDexMode ? variantScope.getMainDexListFile() : null,
                variantScope.getPreDexOutputDir(),
                variantScope.getGlobalScope().getAndroidBuilder(),
                getLogger(),
                variantScope.getInstantRunBuildContext(),
                AndroidGradleOptions.isUserCacheEnabled(variantScope.getGlobalScope().getProject()));
        AndroidTask<TransformTask> dexTask = transformManager.addTransform(
                tasks, variantScope, dexTransform);
        // need to manually make dex task depend on MultiDexTransform since there's no stream
        // consumption making this automatic
        dexTask.optionalDependsOn(tasks, multiDexClassListTask);
        variantScope.addColdSwapBuildTask(dexTask);

        if (preColdSwapTask != null) {
            for (AndroidTask<? extends DefaultTask> task : variantScope.getColdSwapBuildTasks()) {
                task.dependsOn(tasks, preColdSwapTask);
            }
        }
    }

    private boolean isLegacyMultidexMode(@NonNull VariantScope variantScope) {
        return variantScope.getVariantData().getVariantConfiguration().isLegacyMultiDexMode() &&
                (getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE
                        || variantScope.getInstantRunBuildContext().getPatchingPolicy() ==
                        InstantRunPatchingPolicy.PRE_LOLLIPOP);

    }

    private boolean isMinifyEnabled(VariantScope variantScope) {
        return variantScope.getVariantConfiguration().isMinifyEnabled()
                || isTestedAppMinified(variantScope);
    }

    /**
     * Default values if {@code false}, only {@link TestApplicationTaskManager}
     * overrides this, because tested applications might be minified.
     * @return if the tested application is minified
     */
    protected boolean isTestedAppMinified(@NonNull VariantScope variantScope){
        return false;
    }

    /**
     * Create InstantRun related tasks that should be ran right after the java compilation task.
     */
    @NonNull
    private AndroidTask<DefaultTask> createInstantRunAllActionsTasks(
            @NonNull TaskFactory tasks, @NonNull VariantScope variantScope) {

        AndroidTask<DefaultTask> allActionAnchorTask = getAndroidTasks().create(tasks,
                new InstantRunAnchorTaskConfigAction(variantScope));

        TransformManager transformManager = variantScope.getTransformManager();

        ExtractJarsTransform extractJarsTransform = new ExtractJarsTransform(
                ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES),
                ImmutableSet.of(Scope.SUB_PROJECTS));
        AndroidTask<TransformTask> extractJarsTask = transformManager
                .addTransform(tasks, variantScope, extractJarsTransform);

        InstantRunTaskManager instantRunTaskManager = new InstantRunTaskManager(getLogger(),
                variantScope,
                variantScope.getTransformManager(),
                androidTasks,
                tasks);

        variantScope.setInstantRunTaskManager(instantRunTaskManager);
        AndroidTask<BuildInfoLoaderTask> buildInfoLoaderTask =
                instantRunTaskManager.createInstantRunAllTasks(
                        variantScope.getGlobalScope().getExtension().getDexOptions(),
                        androidBuilder::getDexByteCodeConverter,
                        extractJarsTask,
                        allActionAnchorTask,
                        getResMergingScopes(variantScope),
                        new SupplierTask<File>() {
                            private final VariantOutputScope variantOutputScope =
                                    variantScope.getVariantData().getOutputs().get(0).getScope();

                            @Nullable
                            @Override
                            public AndroidTask<?> getBuilderTask() {
                                return variantOutputScope.getManifestProcessorTask();
                            }

                            @Override
                            public File get() {
                                return variantOutputScope.getVariantScope()
                                        .getInstantRunManifestOutputFile();
                            }
                        },
                        true /* addResourceVerifier */);

        if (variantScope.getSourceGenTask() != null) {
            variantScope.getSourceGenTask().dependsOn(tasks, buildInfoLoaderTask);
        }

        return allActionAnchorTask;
    }

    protected void handleJacocoDependencies(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();
        // we add the jacoco jar if coverage is enabled, but we don't add it
        // for test apps as it's already part of the tested app.
        // For library project, since we cannot use the local jars of the library,
        // we add it as well.
        boolean isTestCoverageEnabled = config.getBuildType().isTestCoverageEnabled() &&
                getIncrementalMode(variantScope.getVariantConfiguration()) == IncrementalMode.NONE &&
                (!config.getType().isForTesting() ||
                        (config.getTestedConfig() != null &&
                                config.getTestedConfig().getType() == VariantType.LIBRARY));
        if (isTestCoverageEnabled) {
            AndroidTask<Copy> agentTask = getJacocoAgentTask(tasks);

            // also add a new stream for the jacoco agent Jar
            variantScope.getTransformManager().addStream(OriginalStream.builder()
                    .addContentTypes(TransformManager.CONTENT_JARS)
                    .addScope(Scope.EXTERNAL_LIBRARIES)
                    .setJar(globalScope.getJacocoAgent())
                    .setDependency(agentTask.getName())
                    .build());
        }
    }

    public void createJacocoTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope) {

        AndroidTask<?> task = variantScope.getTransformManager().addTransform(taskFactory,
                variantScope, new JacocoTransform(project.getConfigurations()));

        AndroidTask<Copy> agentTask = getJacocoAgentTask(taskFactory);
        task.dependsOn(taskFactory, agentTask);
    }

    @Nullable
    public AndroidTask<TransformTask> createJackTask(
            @NonNull final TaskFactory tasks,
            @NonNull final VariantScope scope,
            final boolean compileJavaSources) {
        if (scope.getTestedVariantData() != null) {
            scope.getTransformManager().addStream(
                    OriginalStream.builder()
                            .addContentType(ExtendedContentType.JACK)
                            .addScope(Scope.TESTED_CODE)
                            .setJar(scope.getTestedVariantData().getScope().getJackClassesZip())
                            .setDependency(scope.getTestedVariantData().getScope().getJavaCompilerTask().getName())
                            .build());
        }

        // ----- Create PreDex tasks for libraries -----
        JackPreDexTransform preDexPackagedTransform = new JackPreDexTransform(
                androidBuilder,
                globalScope.getExtension().getDexOptions().getJavaMaxHeapSize(),
                scope.getVariantConfiguration().getJackOptions(),
                true);
        AndroidTask<TransformTask> packageTask =
                scope.getTransformManager().addTransform(tasks, scope, preDexPackagedTransform);

        AndroidTask jacocoTask = getJacocoAgentTask(tasks);
        if (jacocoTask != null) {
            packageTask.dependsOn(tasks,
                    scope.getVariantData().getVariantDependency().getPackageConfiguration()
                            .getBuildDependencies(),
                    jacocoTask);
        }

        JackPreDexTransform preDexRuntimeTransform = new JackPreDexTransform(
                androidBuilder,
                globalScope.getExtension().getDexOptions().getJavaMaxHeapSize(),
                scope.getVariantConfiguration().getJackOptions(),
                false);
        scope.getTransformManager().addTransform(tasks, scope, preDexRuntimeTransform);

        // ----- Create Jack Task -----
        JackTransform jackTransform = new JackTransform(scope, isDebugLog(), compileJavaSources);
        scope.getVariantData().jackTransform = jackTransform;
        final AndroidTask<TransformTask> jackTask = scope.getTransformManager().addTransform(
                tasks, scope, jackTransform,
                (transform, task) -> {
                    scope.getVariantData().javaCompilerTask = task;

                    scope.getVariantData().mappingFileProviderTask = new FileSupplier() {
                        @NonNull
                        @Override
                        public Task getTask() {
                            return task;
                        }

                        @Override
                        public File get() {
                            return transform.getMappingFile();
                        }
                    };

                });

        if (jackTask == null) {
            // Error adding JackTransform. A SyncIssue was already emitted at this point.
            getLogger().error("Could not create jack transform.", new Throwable());
            return null;
        }

        // Jack is compiling and also providing the binary and mapping files.
        setJavaCompilerTask(jackTask, tasks, scope);
        setupCompileTaskDependencies(tasks, scope, jackTask);

        jackTask.optionalDependsOn(tasks, scope.getMergeJavaResourcesTask());
        jackTask.dependsOn(tasks, scope.getSourceGenTask());
        return jackTask;
    }

    protected void createDataBindingTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        if (scope.getVariantConfiguration().getJackOptions().isEnabled()) {
            androidBuilder.getErrorReporter().handleSyncError(
                    scope.getVariantConfiguration().getFullName(),
                    SyncIssue.TYPE_JACK_IS_NOT_SUPPORTED,
                    "Data Binding does not support Jack builds yet");
        }

        dataBindingBuilder.setDebugLogEnabled(getLogger().isDebugEnabled());
        AndroidTask<DataBindingProcessLayoutsTask> processLayoutsTask = androidTasks
                .create(tasks, new DataBindingProcessLayoutsTask.ConfigAction(scope));
        scope.setDataBindingProcessLayoutsTask(processLayoutsTask);

        scope.getGenerateRClassTask().dependsOn(tasks, processLayoutsTask);
        processLayoutsTask.dependsOn(tasks, scope.getMergeResourcesTask());

        AndroidTask<DataBindingExportBuildInfoTask> exportBuildInfo = androidTasks
                .create(tasks, new DataBindingExportBuildInfoTask.ConfigAction(scope,
                        dataBindingBuilder.getPrintMachineReadableOutput()));
        scope.setDataBindingExportInfoTask(exportBuildInfo);

        exportBuildInfo.dependsOn(tasks, processLayoutsTask);
        AndroidTask<? extends Task> javaCompilerTask = scope.getJavaCompilerTask();
        if (javaCompilerTask != null) {
            javaCompilerTask.dependsOn(tasks, exportBuildInfo);
        }

        setupCompileTaskDependencies(tasks, scope, exportBuildInfo);

        // support for split apk
        for (BaseVariantOutputData baseVariantOutputData : scope.getVariantData().getOutputs()) {
            final ProcessAndroidResources processResTask =
                    baseVariantOutputData.processResourcesTask;
            if (processResTask != null) {
                processResTask.dependsOn(processLayoutsTask.getName());
            }
        }
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     *
     * @param publishApk if true the generated APK gets published.
     * @param fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
     */
    public void createPackagingTask(@NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope,
            boolean publishApk,
            @Nullable AndroidTask<InstantRunWrapperTask> fullBuildInfoGeneratorTask) {

        final ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();

        boolean signedApk = variantData.isSigned();
        boolean multiOutput = variantData.getOutputs().size() > 1;

        GradleVariantConfiguration variantConfiguration = variantScope.getVariantConfiguration();
        /**
         * PrePackaging step class that will look if the packaging of the main APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split APK. However when a warm swap is
         * possible, it is not necessary to produce immediately the new main SPLIT since the runtime
         * use the resources.ap_ file directly. However, as soon as an incompatible change forcing a
         * cold swap is triggered, the main APK must be rebuilt (even if the resources were changed
         * in a previous build).
         */
        IncrementalMode incrementalMode = getIncrementalMode(variantConfiguration);

        // loop on all outputs. The only difference will be the name of the task, and location
        // of the generated data.
        for (final ApkVariantOutputData variantOutputData : variantData.getOutputs()) {
            final VariantOutputScope variantOutputScope = variantOutputData.getScope();

            final String outputName = variantOutputData.getFullName();
            InstantRunPatchingPolicy patchingPolicy =
                    variantScope.getInstantRunBuildContext().getPatchingPolicy();

            DefaultGradlePackagingScope packagingScope =
                    new DefaultGradlePackagingScope(variantOutputScope);
            PackageApplication.ConfigAction packageConfigAction =
                    new PackageApplication.ConfigAction(
                            packagingScope,
                            patchingPolicy,
                            variantOutputScope.getNativeLibrariesPackagingMode());
            AndroidTask<PackageApplication> packageApp =
                    androidTasks.create(tasks, packageConfigAction);

            packageApp.configure(
                    tasks,
                    task -> variantOutputData.packageAndroidArtifactTask = task);

            CoreSigningConfig signingConfig = packagingScope.getSigningConfig();

            //noinspection VariableNotUsedInsideIf - we use the whole packaging scope below.
            if (signingConfig != null) {
                ValidateSigningTask.ConfigAction configAction =
                        new ValidateSigningTask.ConfigAction(packagingScope);

                AndroidTask<?> validateSigningTask = androidTasks.get(configAction.getName());
                if (validateSigningTask == null) {
                    validateSigningTask = androidTasks.create(tasks, configAction);
                }

                packageApp.dependsOn(tasks, validateSigningTask);
            }

            packageApp.dependsOn(tasks, variantOutputScope.getProcessResourcesTask());

            packageApp.optionalDependsOn(
                    tasks,
                    variantOutputScope.getShrinkResourcesTask(),
                    // TODO: When Jack is converted, add activeDexTask to VariantScope.
                    variantOutputScope.getVariantScope().getJavaCompilerTask(),
                    // TODO: Remove when Jack is converted to AndroidTask.
                    variantData.javaCompilerTask,
                    variantOutputData.packageSplitResourcesTask,
                    variantOutputData.packageSplitAbiTask);

            TransformManager transformManager = variantScope.getTransformManager();

            for (TransformStream stream : transformManager.getStreams(StreamFilter.DEX)) {
                // TODO Optimize to avoid creating too many actions
                packageApp.dependsOn(tasks, stream.getDependencies());
            }

            for (TransformStream stream : transformManager.getStreams(StreamFilter.RESOURCES)) {
                // TODO Optimize to avoid creating too many actions
                packageApp.dependsOn(tasks, stream.getDependencies());
            }
            for (TransformStream stream : transformManager.getStreams(StreamFilter.NATIVE_LIBS)) {
                // TODO Optimize to avoid creating too many actions
                packageApp.dependsOn(tasks, stream.getDependencies());
            }

            variantScope.setPackageApplicationTask(packageApp);

            AndroidTask<?> appTask = packageApp;

            boolean useOldPackaging = AndroidGradleOptions.useOldPackaging(
                    variantScope.getGlobalScope().getProject());
            if (signedApk) {
                if (useOldPackaging && variantData.getZipAlignEnabled()) {
                    AndroidTask<ZipAlign> zipAlignTask = androidTasks.create(
                            tasks, new ZipAlign.ConfigAction(variantOutputScope));
                    zipAlignTask.dependsOn(tasks, packageApp);

                    appTask = zipAlignTask;
                }

                /*
                 * There may be a zip align task in the variant output scope, even if we don't
                 * need one for this because we're using new packaging.
                 */
                if (variantData.getZipAlignEnabled()
                        && variantOutputScope.getSplitZipAlignTask() != null) {
                    appTask.dependsOn(tasks, variantOutputScope.getSplitZipAlignTask());
                }
            }

            checkState(variantScope.getAssembleTask() != null);
            if (fullBuildInfoGeneratorTask != null) {
                fullBuildInfoGeneratorTask.dependsOn(tasks, appTask);
                variantScope.getAssembleTask().dependsOn(tasks, fullBuildInfoGeneratorTask.getName());
            }

            // Add an assemble task
            if (multiOutput) {
                // create a task for this output
                variantOutputScope.setAssembleTask(createAssembleTask(tasks, variantOutputData));

                // variant assemble task depends on each output assemble task.
                variantScope.getAssembleTask().dependsOn(
                        tasks, variantOutputScope.getAssembleTask());
            } else {
                // single output
                variantOutputScope.setAssembleTask(variantScope.getAssembleTask());
                variantOutputData.assembleTask = variantData.assembleVariantTask;
            }

            if (!signedApk && variantOutputData.packageSplitResourcesTask != null) {
                // in case we are not signing the resulting APKs and we have some pure splits
                // we should manually copy them from the intermediate location to the final
                // apk location unmodified.
                final String appTaskName = appTask.getName();
                AndroidTask<Copy> copySplitTask = androidTasks.create(
                        tasks,
                        variantOutputScope.getTaskName("copySplit"),
                        Copy.class,
                        copyTask -> {
                            copyTask.setDestinationDir(getGlobalScope().getApkLocation());
                            copyTask.from(
                                    variantOutputData
                                            .packageSplitResourcesTask
                                            .getOutputDirectory());
                            copyTask.mustRunAfter(appTaskName);
                        });
                variantOutputScope.getAssembleTask().dependsOn(tasks, copySplitTask);
            }
            variantOutputScope.getAssembleTask().dependsOn(tasks, appTask);

            if (publishApk) {
                final String projectBaseName = globalScope.getProjectBaseName();

                // if this variant is the default publish config or we also should publish non
                // defaults, proceed with declaring our artifacts.
                if (getExtension().getDefaultPublishConfig().equals(outputName)) {
                    appTask.configure(tasks, packageTask -> project.getArtifacts().add("default",
                            new ApkPublishArtifact(
                                    projectBaseName,
                                    null,
                                    (FileSupplier) packageTask)));

                    for (FileSupplier outputFileProvider :
                            variantOutputData.getSplitOutputFileSuppliers()) {
                        project.getArtifacts().add("default",
                                new ApkPublishArtifact(projectBaseName, null, outputFileProvider));
                    }

                    try {
                        if (variantOutputData.getMetadataFile() != null) {
                            project.getArtifacts().add(
                                    "default" + VariantDependencies.CONFIGURATION_METADATA,
                                    new MetadataPublishArtifact(projectBaseName, null,
                                            variantOutputData.getMetadataFile()));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (variantData.getMappingFileProvider() != null) {
                        project.getArtifacts().add(
                                "default" + VariantDependencies.CONFIGURATION_MAPPING,
                                new MappingPublishArtifact(projectBaseName, null,
                                        variantData.getMappingFileProvider()));
                    }
                }

                if (getExtension().getPublishNonDefault()) {
                    appTask.configure(tasks, packageTask -> project.getArtifacts().add(
                            variantData.getVariantDependency().getPublishConfiguration().getName(),
                            new ApkPublishArtifact(
                                    projectBaseName,
                                    null,
                                    (FileSupplier) packageTask)));

                    for (FileSupplier outputFileProvider :
                            variantOutputData.getSplitOutputFileSuppliers()) {
                        project.getArtifacts().add(
                                variantData.getVariantDependency().getPublishConfiguration().getName(),
                                new ApkPublishArtifact(
                                        projectBaseName,
                                        null,
                                        outputFileProvider));
                    }

                    try {
                        if (variantOutputData.getMetadataFile() != null) {
                            project.getArtifacts().add(
                                    variantData.getVariantDependency().getMetadataConfiguration().getName(),
                                    new MetadataPublishArtifact(
                                            projectBaseName,
                                            null,
                                            variantOutputData.getMetadataFile()));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    if (variantData.getMappingFileProvider() != null) {
                        project.getArtifacts().add(
                                variantData.getVariantDependency().getMappingConfiguration().getName(),
                                new MappingPublishArtifact(
                                        projectBaseName,
                                        null,
                                        variantData.getMappingFileProvider()));
                    }

                    if (variantData.classesJarTask != null) {
                        project.getArtifacts().add(
                                variantData.getVariantDependency().getClassesConfiguration().getName(),
                                variantData.classesJarTask);
                    }
                }
            }
        }


        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            AndroidTask<InstallVariantTask> installTask = androidTasks.create(
                    tasks, new InstallVariantTask.ConfigAction(variantScope));
            installTask.dependsOn(tasks, variantScope.getAssembleTask());
        }

        if (getExtension().getLintOptions().isCheckReleaseBuilds()
                && (incrementalMode == IncrementalMode.NONE)) {
            createLintVitalTask(tasks, variantData);
        }

        // add an uninstall task
        final AndroidTask<UninstallTask> uninstallTask = androidTasks.create(
                tasks, new UninstallTask.ConfigAction(variantScope));

        tasks.named(UNINSTALL_ALL, uninstallAll -> uninstallAll.dependsOn(uninstallTask.getName()));
    }

    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks,
            @NonNull final BaseVariantOutputData variantOutputData) {
        return androidTasks.create(
                tasks,
                variantOutputData.getScope().getTaskName("assemble"),
                task -> {
                    variantOutputData.assembleTask = task;
                });
    }

    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        return androidTasks.create(
                tasks,
                variantData.getScope().getTaskName("assemble"),
                task -> {
                    variantData.assembleVariantTask = task;
                });
    }

    @NonNull
    public AndroidTask<DefaultTask> createAssembleTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantDimensionData dimensionData) {
        final String sourceSetName =
                StringHelper.capitalize(dimensionData.getSourceSet().getName());
        return androidTasks.create(
                tasks,
                "assemble" + sourceSetName,
                assembleTask -> {
                    assembleTask.setDescription("Assembles all " + sourceSetName + " builds.");
                    assembleTask.setGroup(BasePlugin.BUILD_GROUP);
                });
    }

    public AndroidTask<Copy> getJacocoAgentTask(TaskFactory tasks) {
        if (jacocoAgentTask == null) {
            jacocoAgentTask = androidTasks.create(tasks, new JacocoAgentConfigAction(globalScope));
        }
        return jacocoAgentTask;
    }

    /**
     * creates a zip align. This does not use convention mapping, and is meant to let other plugin
     * create zip align tasks.
     *
     * @param name          the name of the task
     * @param buildContext  the InstantRun build context
     * @param inputFile     the input file
     * @param outputFile    the output file
     * @return the task
     */
    @NonNull
    public ZipAlign createZipAlignTask(
            @NonNull String name,
            @NonNull InstantRunBuildContext buildContext,
            @NonNull File inputFile,
            @NonNull File outputFile) {
        // Add a task to zip align application package
        ZipAlign zipAlignTask = project.getTasks().create(name, ZipAlign.class);

        zipAlignTask.setInputFile(inputFile);
        zipAlignTask.setOutputFile(outputFile);
        zipAlignTask.setInstantRunBuildContext(buildContext);
        ConventionMappingHelper.map(zipAlignTask, "zipAlignExe", (Callable<File>) () -> {
            final TargetInfo info = androidBuilder.getTargetInfo();
            if (info != null) {
                String path = info.getBuildTools().getPath(BuildToolInfo.PathId.ZIP_ALIGN);
                if (path != null) {
                    return new File(path);
                }
            }

            return null;
        });

        return zipAlignTask;
    }

    protected void createMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope,
            boolean createJarFile) {
        doCreateMinifyTransform(taskFactory,
                variantScope,
                null /*mappingConfiguration*/, // No mapping in non-test modules.
                createJarFile);
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules.
     */
    protected final void doCreateMinifyTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull final VariantScope variantScope,
            @Nullable Configuration mappingConfiguration,
            boolean createJarFile) {
        if (variantScope.getVariantData().getVariantConfiguration().getBuildType().isUseProguard()) {
            createProguardTransform(taskFactory, variantScope, mappingConfiguration, createJarFile);
            createShrinkResourcesTransform(taskFactory, variantScope);
        } else {
            // Since the built-in class shrinker does not obfuscate, there's no point running
            // it on the test APK (it also doesn't have a -dontshrink mode).
            if (variantScope.getTestedVariantData() == null) {
                createNewShrinkerTransform(variantScope, taskFactory);
                createShrinkResourcesTransform(taskFactory, variantScope);
            }
        }
    }

    private void createNewShrinkerTransform(VariantScope scope, TaskFactory taskFactory) {
        NewShrinkerTransform transform = new NewShrinkerTransform(scope);
        applyProguardConfig(transform, scope.getVariantData());

        if (getIncrementalMode(scope.getVariantConfiguration()) != IncrementalMode.NONE) {
            //TODO: This is currently overly broad, as finding the actual application class
            //      requires manually parsing the manifest (See CreateManifestKeepList)
            transform.keep("class ** extends android.app.Application {*;}");
            transform.keep("class com.android.tools.fd.** {*;}");
        }

        scope.getTransformManager().addTransform(taskFactory, scope, transform);
    }

    private void createProguardTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope variantScope,
            @Nullable Configuration mappingConfiguration,
            boolean createJarFile) {
        if (getIncrementalMode(variantScope.getVariantConfiguration()) != IncrementalMode.NONE) {
            logger.warn("Instant Run: Proguard is not compatible with instant run. "
                            + "It has been disabled for {}",
                    variantScope.getVariantConfiguration().getFullName());
            return;
        }

        final BaseVariantData<? extends BaseVariantOutputData> variantData = variantScope
                .getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final BaseVariantData testedVariantData = variantScope.getTestedVariantData();

        ProGuardTransform transform = new ProGuardTransform(variantScope, createJarFile);

        if (testedVariantData != null) {
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(
                    testedVariantData.getVariantConfiguration()::getTestProguardFiles);

            // register the mapping file which may or may not exists (only exist if obfuscation)
            // is enabled.
            transform.applyTestedMapping(testedVariantData.getMappingFile());
        } else if (isTestedAppMinified(variantScope)){
            applyProguardDefaultsForTest(transform);
            // All -dontwarn rules for test dependencies should go in here:
            transform.setConfigurationFiles(variantConfig::getTestProguardFiles);
            transform.applyTestedMapping(mappingConfiguration);
        } else {
            applyProguardConfig(transform, variantData);

            if (mappingConfiguration != null) {
                transform.applyTestedMapping(mappingConfiguration);
            }
        }

        AndroidTask<?> task = variantScope.getTransformManager().addTransform(taskFactory,
                variantScope, transform,
                (proGuardTransform, proGuardTask) ->
                        variantData.mappingFileProviderTask = new FileSupplier() {
                            @NonNull
                            @Override
                            public Task getTask() {
                        return proGuardTask;
                    }

                            @Override
                            public File get() {
                        return proGuardTransform.getMappingFile();
                    }
                        });

        if (mappingConfiguration != null) {
            verifyNotNull(task);
            task.dependsOn(taskFactory, mappingConfiguration);
        }
    }

    private static void applyProguardDefaultsForTest(ProGuardTransform transform) {
        // Don't remove any code in tested app.
        transform.dontshrink();
        transform.dontoptimize();

        // We can't call dontobfuscate, since that would make ProGuard ignore the mapping file.
        transform.keep("class * {*;}");
        transform.keep("interface * {*;}");
        transform.keep("enum * {*;}");
        transform.keepattributes();
    }

    private void createShrinkResourcesTransform(
            @NonNull TaskFactory taskFactory,
            @NonNull VariantScope scope) {
        CoreBuildType buildType = scope.getVariantConfiguration().getBuildType();

        if (!buildType.isShrinkResources()) {
            // The user didn't enable resource shrinking, silently move on.
            return;
        }

        if (!scope.useResourceShrinker()) {
            // The user enabled resource shrinking, but we disabled it for some reason. Try to
            // explain.

            if (getIncrementalMode(scope.getVariantConfiguration()) != IncrementalMode.NONE) {
                logger.warn("Instant Run: Resource shrinker automatically disabled for {}",
                        scope.getVariantConfiguration().getFullName());
                return;
            }

            if (buildType.isMinifyEnabled() && !buildType.isUseProguard()) {
                androidBuilder.getErrorReporter().handleSyncError(
                        null,
                        SyncIssue.TYPE_GENERIC,
                        "Built-in class shrinker and resource shrinking are not supported yet.");
                return;
            }

            return;
        }


        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package
        for (final BaseVariantOutputData variantOutputData : scope.getVariantData().getOutputs()) {
            VariantOutputScope variantOutputScope = variantOutputData.getScope();

            ShrinkResourcesTransform shrinkResTransform = new ShrinkResourcesTransform(
                    variantOutputData,
                    variantOutputScope.getProcessResourcePackageOutputFile(),
                    variantOutputScope.getShrinkedResourcesFile(),
                    androidBuilder,
                    logger);
            AndroidTask<TransformTask> shrinkTask = scope.getTransformManager()
                    .addTransform(taskFactory, variantOutputScope, shrinkResTransform);
            // need to record this task since the package task will not depend
            // on it through the transform manager.
            variantOutputScope.setShrinkResourcesTask(shrinkTask);
        }
    }

    private void applyProguardConfig(
            ProguardConfigurable transform,
            final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        transform.setConfigurationFiles(() -> {
            Set<File> proguardFiles = variantConfig.getProguardFiles(
                    true,
                    Collections.singletonList(ProguardFiles.getDefaultProguardFile(
                            TaskManager.DEFAULT_PROGUARD_CONFIG_FILE, project)));

            // use the first output when looking for the proguard rule output of
            // the aapt task. The different outputs are not different in a way that
            // makes this rule file different per output.
            BaseVariantOutputData outputData = variantData.getOutputs().get(0);
            proguardFiles.add(outputData.processResourcesTask.getProguardOutputFile());
            return proguardFiles;
        });

        if (variantData.getType() == LIBRARY) {
            transform.keep("class **.R");
            transform.keep("class **.R$*");
        }

        if (variantData.getVariantConfiguration().isTestCoverageEnabled()) {
            // when collecting coverage, don't remove the JaCoCo runtime
            transform.keep("class com.vladium.** {*;}");
            transform.keep("class org.jacoco.** {*;}");
            transform.keep("interface org.jacoco.** {*;}");
            transform.dontwarn("org.jacoco.**");
        }
    }

    public void createReportTasks(
            TaskFactory tasks,
            final List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList) {
        androidTasks.create(tasks, "androidDependencies", DependencyReportTask.class,
                task -> {
                    task.setDescription("Displays the Android dependencies of the project.");
                    task.setVariants(variantDataList);
                    task.setGroup(ANDROID_GROUP);
                });

        androidTasks.create(tasks, "signingReport", SigningReportTask.class,
                task -> {
                    task.setDescription("Displays the signing info for each variant.");
                    task.setVariants(variantDataList);
                    task.setGroup(ANDROID_GROUP);
                });
    }

    public void createAnchorTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        createPreBuildTasks(tasks, scope);

        // also create sourceGenTask
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        scope.setSourceGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Sources"),
                Task.class,
                task -> {
                    variantData.sourceGenTask = task;
                }));
        // and resGenTask
        scope.setResourceGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Resources"),
                Task.class,
                task -> {
                    variantData.resourceGenTask = task;
                }));

        scope.setAssetGenTask(androidTasks.create(tasks,
                scope.getTaskName("generate", "Assets"),
                Task.class,
                task -> {
                    variantData.assetGenTask = task;
                }));

        if (!variantData.getType().isForTesting()
                && variantData.getVariantConfiguration().getBuildType().isTestCoverageEnabled()) {
            scope.setCoverageReportTask(androidTasks.create(tasks,
                    scope.getTaskName("create", "CoverageReport"),
                    Task.class,
                    task -> {
                        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                        task.setDescription(String.format(
                                "Creates test coverage reports for the %s variant.",
                                variantData.getName()));
                    }));
        }

        // and compile task
        createCompileAnchorTask(tasks, scope);
    }

    private void createPreBuildTasks(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        scope.setPreBuildTask(androidTasks.create(tasks,
                scope.getTaskName("pre", "Build"), task -> {
                    variantData.preBuildTask = task;
                }));
        scope.getPreBuildTask().dependsOn(tasks, MAIN_PREBUILD);

        if (isMinifyEnabled(scope)) {
            scope.getPreBuildTask().dependsOn(tasks, EXTRACT_PROGUARD_FILES);
        }

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        VariantDependencies configurationDependencies = variantData.getVariantDependency();

        AndroidTask<PrepareDependenciesTask> prepareDependenciesTask = androidTasks.create(tasks,
                new PrepareDependenciesTask.ConfigAction(scope, configurationDependencies));
        scope.setPrepareDependenciesTask(prepareDependenciesTask);

        prepareDependenciesTask.dependsOn(tasks, scope.getPreBuildTask());

        dependencyManager.addDependenciesToPrepareTask(tasks, variantData, prepareDependenciesTask);
    }

    private void createCompileAnchorTask(@NonNull TaskFactory tasks, @NonNull final VariantScope scope) {
        final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
        scope.setCompileTask(androidTasks.create(tasks, new TaskConfigAction<Task>() {
            @NonNull
            @Override
            public String getName() {
                return scope.getTaskName("compile", "Sources");
            }

            @NonNull
            @Override
            public Class<Task> getType() {
                return Task.class;
            }

            @Override
            public void execute(@NonNull Task task) {
                variantData.compileTask = task;
                variantData.compileTask.setGroup(BUILD_GROUP);
            }
        }));
        scope.getAssembleTask().dependsOn(tasks, scope.getCompileTask());
    }

    public void createCheckManifestTask(@NonNull TaskFactory tasks, @NonNull VariantScope scope) {
        scope.setCheckManifestTask(
                androidTasks.create(tasks, getCheckManifestConfig(scope)));
        scope.getCheckManifestTask().dependsOn(tasks, scope.getPreBuildTask());
        scope.getPrepareDependenciesTask().dependsOn(tasks, scope.getCheckManifestTask());
    }

    protected CheckManifest.ConfigAction getCheckManifestConfig(@NonNull VariantScope scope){
        return new CheckManifest.ConfigAction(scope, false);
    }

    @NonNull
    protected Logger getLogger() {
        return logger;
    }

    @NonNull
    public AndroidTaskRegistry getAndroidTasks() {
        return androidTasks;
    }

    public void addDataBindingDependenciesIfNecessary(DataBindingOptions options) {
        if (!options.isEnabled()) {
            return;
        }
        String version = Objects.firstNonNull(options.getVersion(),
                dataBindingBuilder.getCompilerVersion());
        project.getDependencies().add("compile", SdkConstants.DATA_BINDING_LIB_ARTIFACT + ":"
                + dataBindingBuilder.getLibraryVersion(version));
        project.getDependencies().add("compile", SdkConstants.DATA_BINDING_BASELIB_ARTIFACT + ":"
                + dataBindingBuilder.getBaseLibraryVersion(version));
        project.getDependencies().add("provided",
                SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" +
                version);
        if (options.getAddDefaultAdapters()) {
            project.getDependencies()
                    .add("compile", SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT + ":" +
                    dataBindingBuilder.getBaseAdaptersVersion(version));
        }
    }
}
