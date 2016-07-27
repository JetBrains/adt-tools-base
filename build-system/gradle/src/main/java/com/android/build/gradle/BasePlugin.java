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

package com.android.build.gradle;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.google.common.base.Preconditions.checkState;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.ExecutionConfigurationUtil;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryCache;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NativeLibraryFactoryImpl;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskContainerAdaptor;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.ToolingRegistryProvider;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BuildTypeFactory;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavorFactory;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.SigningConfigFactory;
import com.android.build.gradle.internal.model.ModelBuilder;
import com.android.build.gradle.internal.model.NativeModelBuilder;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.internal.profile.RecordingBuildListener;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.JackPreDexTransform;
import com.android.builder.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.internal.compiler.JackConversionCache;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.ProcessRecorder;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.GradleBuildProfileSpan.ExecutionType;
import com.android.builder.profile.ProcessRecorderFactory;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.TargetInfo;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.internal.ExecutorSingleton;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.Channel;
import com.android.repository.api.Downloader;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.downloader.LocalFileAwareDownloader;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.utils.ILogger;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import android.databinding.tool.DataBindingBuilder;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

/**
 * Base class for all Android plugins
 */
public abstract class BasePlugin implements ToolingRegistryProvider {

    private static final GradleVersion GRADLE_MIN_VERSION = GradleVersion.parse("2.14.1");
    /** default retirement age in days since its inception date for RC or beta versions. */
    private static final int DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE_IN_DAYS = 40;


    protected BaseExtension extension;

    protected VariantManager variantManager;

    protected TaskManager taskManager;

    protected Project project;

    protected SdkHandler sdkHandler;

    private NdkHandler ndkHandler;

    protected AndroidBuilder androidBuilder;

    protected DataBindingBuilder dataBindingBuilder;

    protected Instantiator instantiator;

    protected VariantFactory variantFactory;

    private ToolingModelBuilderRegistry registry;

    private JacocoPlugin jacocoPlugin;

    private LoggerWrapper loggerWrapper;

    private ExtraModelInfo extraModelInfo;

    private String creator;

    private DependencyManager dependencyManager;

    private boolean hasCreatedTasks = false;

    BasePlugin(@NonNull Instantiator instantiator, @NonNull ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator;
        this.registry = registry;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        verifyRetirementAge();

        ModelBuilder.clearCaches();
    }

    /**
     * Verify that this plugin execution is within its public time range.
     */
    private void verifyRetirementAge() {

        Manifest manifest;
        URLClassLoader cl = (URLClassLoader) getClass().getClassLoader();
        try {
            URL url = cl.findResource("META-INF/MANIFEST.MF");
            manifest = new Manifest(url.openStream());
        } catch (IOException ignore) {
            return;
        }

        int retirementAgeInDays =
                getRetirementAgeInDays(manifest.getMainAttributes().getValue("Plugin-Version"));

        // if this plugin version will never be outdated, return.
        if (retirementAgeInDays == -1) {
            return;
        }

        String inceptionDateAttr = manifest.getMainAttributes().getValue("Inception-Date");
        // when running in unit tests, etc... the manifest entries are absent.
        if (inceptionDateAttr == null) {
            return;
        }
        List<String> items = ImmutableList.copyOf(Splitter.on(':').split(inceptionDateAttr));
        GregorianCalendar inceptionDate = new GregorianCalendar(Integer.parseInt(items.get(0)),
                Integer.parseInt(items.get(1)), Integer.parseInt(items.get(2)));


        Calendar now = GregorianCalendar.getInstance();
        long nowTimestamp = now.getTimeInMillis();
        long inceptionTimestamp = inceptionDate.getTimeInMillis();
        long days = TimeUnit.DAYS.convert(nowTimestamp - inceptionTimestamp, TimeUnit.MILLISECONDS);
        if (days > retirementAgeInDays) {
            // this plugin is too old.
            String dailyOverride = System.getenv("ANDROID_DAILY_OVERRIDE");
            final MessageDigest crypt;
            try {
                crypt = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                return;
            }
            crypt.reset();
            // encode the day, not the current time.
            try {
                crypt.update(String.format("%1$s:%2$s:%3$s",
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        now.get(Calendar.DATE)).getBytes("utf8"));
            } catch (UnsupportedEncodingException e) {
                return;
            }
            String overrideValue = new BigInteger(1, crypt.digest()).toString(16);
            if (dailyOverride == null) {
                String message = "Plugin is too old, please update to a more recent version, or " +
                        "set ANDROID_DAILY_OVERRIDE environment variable to \"" + overrideValue + '"';
                System.err.println(message);
                throw new RuntimeException(message);
            } else {
                if (!dailyOverride.equals(overrideValue)) {
                    String message = "Plugin is too old and ANDROID_DAILY_OVERRIDE value is " +
                            "also outdated, please use new value :\"" + overrideValue + '"';
                    System.err.println(message);
                    throw new RuntimeException(message);
                }
            }
        }
    }

    /**
     * Returns the retirement age for this plugin depending on its version string, or -1 if this
     * plugin version will never become obsolete
     * @param version the plugin full version, like 1.3.4-preview5 or 1.0.2 or 1.2.3-beta4
     * @return the retirement age in days or -1 if no retirement
     */
    private static int getRetirementAgeInDays(@Nullable String version) {
        if (version == null || version.contains("rc") || version.contains("beta")
                || version.contains("alpha") || version.contains("preview")) {
            return DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE_IN_DAYS;
        }
        return -1;
    }

    protected abstract Class<? extends BaseExtension> getExtensionClass();
    protected abstract VariantFactory createVariantFactory();
    protected abstract TaskManager createTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry);

    /**
     * Return whether this plugin creates Android library.  Should be overridden if true.
     */
    protected boolean isLibrary() {
        return false;
    }

    @VisibleForTesting
    VariantManager getVariantManager() {
        return variantManager;
    }

    protected ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.getLogger());
        }

        return loggerWrapper;
    }


    protected void apply(@NonNull Project project) {
        checkPluginVersion();

        this.project = project;
        ExecutionConfigurationUtil.setThreadPoolSize(project);
        checkPathForErrors();
        checkModulesForErrors();

        ProfilerInitializer.init(project);

        String benchmarkName = AndroidGradleOptions.getBenchmarkName(project);
        String benchmarkMode = AndroidGradleOptions.getBenchmarkMode(project);
        if (benchmarkName != null && benchmarkMode != null) {
            ProcessRecorder.setBenchmark(benchmarkName, benchmarkMode);
        }

        AndroidStudioStats.GradleBuildProject.PluginType pluginType =
                AndroidStudioStats.GradleBuildProject.PluginType.UNKNOWN_PLUGIN_TYPE;
        if (this instanceof AppPlugin) {
            pluginType = AndroidStudioStats.GradleBuildProject.PluginType.APPLICATION;
        } else if (this instanceof LibraryPlugin) {
            pluginType = AndroidStudioStats.GradleBuildProject.PluginType.LIBRARY;
        } else if (this instanceof TestPlugin) {
            pluginType = AndroidStudioStats.GradleBuildProject.PluginType.TEST;
        }

        ProcessRecorder.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(pluginType)
                .setPluginGeneration(
                        AndroidStudioStats.GradleBuildProject.PluginGeneration.FIRST);

        ThreadRecorder.get().record(ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.getPath(), null /*variantName*/, new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        configureProject();
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                project.getPath(), null /*variantName*/, new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createExtension();
                        return null;
                    }
                });

        ThreadRecorder.get().record(ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                project.getPath(), null /*variantName*/, new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        createTasks();
                        return null;
                    }
                });

        // Apply additional plugins
        for (String plugin : AndroidGradleOptions.getAdditionalPlugins(project)) {
            project.apply(ImmutableMap.of("plugin", plugin));
        }
    }

    protected void configureProject() {
        extraModelInfo = new ExtraModelInfo(project, isLibrary());
        checkGradleVersion();
        sdkHandler = new SdkHandler(project, getLogger());

        project.afterEvaluate(p -> {
            // TODO: Read flag from extension.
            if (!p.getGradle().getStartParameter().isOffline()
                    && AndroidGradleOptions.getUseSdkDownload(p)) {
                SdkLibData sdkLibData =
                        SdkLibData.download(getDownloader(), getSettingsController());
                dependencyManager.setSdkLibData(sdkLibData);
                sdkHandler.setSdkLibData(sdkLibData);
            }
        });

        androidBuilder = new AndroidBuilder(
                project == project.getRootProject() ? project.getName() : project.getPath(),
                creator,
                new GradleProcessExecutor(project),
                new GradleJavaProcessExecutor(project),
                extraModelInfo,
                getLogger(),
                isVerbose());
        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                extraModelInfo.getErrorFormatMode() ==
                        ExtraModelInfo.ErrorFormatMode.MACHINE_PARSABLE);
        project.getPlugins().apply(JavaBasePlugin.class);

        jacocoPlugin = project.getPlugins().apply(JacocoPlugin.class);

        project.getTasks().getByName("assemble").setDescription(
                "Assembles all variants of all applications and secondary packages.");

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        project.getGradle().addBuildListener(new BuildListener() {
            private final LibraryCache libraryCache = LibraryCache.getCache();

            @Override
            public void buildStarted(Gradle gradle) { }

            @Override
            public void settingsEvaluated(Settings settings) { }

            @Override
            public void projectsLoaded(Gradle gradle) { }

            @Override
            public void projectsEvaluated(Gradle gradle) { }

            @Override
            public void buildFinished(BuildResult buildResult) {
                ExecutorSingleton.shutdown();
                sdkHandler.unload();
                ThreadRecorder.get().record(ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                        project.getPath(), null, new Recorder.Block() {
                            @Override
                            public Void call() throws Exception {
                                PreDexCache.getCache().clear(
                                        new File(project.getRootProject().getBuildDir(),
                                                FD_INTERMEDIATES + "/dex-cache/cache.xml"),
                                        getLogger());
                                JackConversionCache.getCache().clear(
                                        new File(project.getRootProject().getBuildDir(),
                                                FD_INTERMEDIATES + "/jack-cache/cache.xml"),
                                        getLogger());
                                libraryCache.unload();
                                Main.clearInternTables();
                                return null;
                            }
                        });
            }
        });
        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(
                new TaskExecutionGraphListener() {
                    @Override
                    public void graphPopulated(TaskExecutionGraph taskGraph) {
                        for (Task task : taskGraph.getAllTasks()) {
                            if (task instanceof TransformTask) {
                                Transform transform = ((TransformTask) task).getTransform();
                                if (transform instanceof DexTransform) {
                                    PreDexCache.getCache().load(
                                            new File(project.getRootProject().getBuildDir(),
                                                    FD_INTERMEDIATES + "/dex-cache/cache.xml"));
                                    break;
                                } else if (transform instanceof JackPreDexTransform) {
                                    JackConversionCache.getCache().load(
                                            new File(project.getRootProject().getBuildDir(),
                                                    FD_INTERMEDIATES + "/jack-cache/cache.xml"));
                                    break;
                                }
                            }
                        }
                    }
                });
    }


    private void createExtension() {
        final NamedDomainObjectContainer<BuildType> buildTypeContainer = project.container(
                BuildType.class,
                new BuildTypeFactory(instantiator, project, project.getLogger()));
        final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer = project.container(
                ProductFlavor.class,
                new ProductFlavorFactory(instantiator, project, project.getLogger(), extraModelInfo));
        final NamedDomainObjectContainer<SigningConfig>  signingConfigContainer = project.container(
                SigningConfig.class,
                new SigningConfigFactory(instantiator));

        extension = project.getExtensions().create("android", getExtensionClass(),
                project, instantiator, androidBuilder, sdkHandler,
                buildTypeContainer, productFlavorContainer, signingConfigContainer,
                extraModelInfo, isLibrary());

        // create the default mapping configuration.
        project.getConfigurations().create("default" + VariantDependencies.CONFIGURATION_MAPPING)
                .setDescription("Configuration for default mapping artifacts.");
        project.getConfigurations().create("default" + VariantDependencies.CONFIGURATION_METADATA)
                .setDescription("Metadata for the produced APKs.");

        dependencyManager = new DependencyManager(
                project,
                extraModelInfo,
                sdkHandler);

        ndkHandler = new NdkHandler(
                project.getRootDir(),
                null, /* compileSkdVersion, this will be set in afterEvaluate */
                "gcc",
                "" /*toolchainVersion*/);

        taskManager = createTaskManager(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                registry);

        variantFactory = createVariantFactory();
        variantManager = new VariantManager(
                project,
                androidBuilder,
                extension,
                variantFactory,
                taskManager,
                instantiator);

        // Register a builder for the custom tooling model
        ModelBuilder modelBuilder = new ModelBuilder(
                androidBuilder,
                variantManager,
                taskManager,
                extension,
                extraModelInfo,
                ndkHandler,
                new NativeLibraryFactoryImpl(ndkHandler),
                isLibrary(),
                AndroidProject.GENERATION_ORIGINAL);
        registry.register(modelBuilder);

        // Register a builder for the native tooling model
        NativeModelBuilder nativeModelBuilder = new NativeModelBuilder(variantManager);
        registry.register(nativeModelBuilder);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded(new Action<SigningConfig>() {
            @Override
            public void execute(SigningConfig signingConfig) {
                variantManager.addSigningConfig(signingConfig);
            }
        });


        buildTypeContainer.whenObjectAdded(new Action<BuildType>() {
            @Override
            public void execute(BuildType buildType) {
                SigningConfig signingConfig = signingConfigContainer.findByName(BuilderConstants.DEBUG);
                buildType.init(signingConfig);
                variantManager.addBuildType(buildType);
            }
        });

        productFlavorContainer.whenObjectAdded(new Action<ProductFlavor>() {
            @Override
            public void execute(ProductFlavor productFlavor) {
                variantManager.addProductFlavor(productFlavor);
            }
        });

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved(
                new UnsupportedAction("Removing signingConfigs is not supported."));
        buildTypeContainer.whenObjectRemoved(
                new UnsupportedAction("Removing build types is not supported."));
        productFlavorContainer.whenObjectRemoved(
                new UnsupportedAction("Removing product flavors is not supported."));

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(
                buildTypeContainer, productFlavorContainer, signingConfigContainer);
    }

    private static class UnsupportedAction implements Action<Object> {

        private final String message;

        UnsupportedAction(String message) {
            this.message = message;
        }

        @Override
        public void execute(Object o) {
            throw new UnsupportedOperationException(message);
        }
    }

    private void createTasks() {
        ThreadRecorder.get().record(ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(), null,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        taskManager.createTasksBeforeEvaluate(
                                new TaskContainerAdaptor(project.getTasks()));
                        return null;
                    }
                });

        project.afterEvaluate(project -> {
            ThreadRecorder.get().record(ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                    project.getPath(), null,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() throws Exception {
                            createAndroidTasks(false);
                            return null;
                        }
                    });
        });
    }

    private void checkGradleVersion() {
        String currentVersion = project.getGradle().getGradleVersion();
        if (GRADLE_MIN_VERSION.compareTo(currentVersion) > 0) {
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage =
                    String.format(
                            "Minimum supported Gradle version is %s. Current version is %s. "
                                    + "If using the gradle wrapper, try editing the distributionUrl in %s "
                                    + "to gradle-%s-all.zip",
                            GRADLE_MIN_VERSION,
                            currentVersion,
                            file.getAbsolutePath(),
                            GRADLE_MIN_VERSION);
            if (AndroidGradleOptions.overrideGradleVersionCheck(project)) {
                getLogger().warning(errorMessage);
                getLogger()
                        .warning(
                                "As %s is set, continuing anyways.",
                                AndroidGradleOptions.GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY);
            } else {
                extraModelInfo.handleSyncError(
                        GRADLE_MIN_VERSION.toString(), SyncIssue.TYPE_GRADLE_TOO_OLD, errorMessage);
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks(boolean force) {
        // Make sure unit tests set the required fields.
        checkState(extension.getBuildToolsRevision() != null,
                "buildToolsVersion is not specified.");
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

        ndkHandler.setCompileSdkVersion(extension.getCompileSdkVersion());

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        ensureTargetSetup();

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force
                && (!project.getState().getExecuted() || project.getState().getFailure() != null)
                && SdkHandler.sTestSdkFolder == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        extension.disableWrite();

        ProcessRecorder.getProject(project.getPath())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString());

        // setup SDK repositories.
        sdkHandler.addLocalRepositories(project);

        taskManager.addDataBindingDependenciesIfNecessary(extension.getDataBinding());
        ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_CREATE_ANDROID_TASKS,
                project.getPath(), null,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        variantManager.createAndroidTasks();
                        ApiObjectFactory apiObjectFactory = new ApiObjectFactory(
                                androidBuilder, extension, variantFactory, instantiator);
                        for (BaseVariantData variantData : variantManager.getVariantDataList())  {
                            apiObjectFactory.create(variantData);
                        }
                        return null;
                    }
                });

        // Create and read external native build JSON files depending on what's happening right
        // now.
        //
        // CREATE PHASE:
        // Creates JSONs by shelling out to external build system when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        //   - *and* AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL is set
        //      or JSON files don't exist or are out-of-date.
        // Create phase may cause ProcessException (from cmake.exe for example)
        //
        // READ PHASE:
        // Reads and deserializes JSONs when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        // Read phase may produce IOException if the file can't be read for standard IO reasons.
        // Read phase may produce JsonSyntaxException in the case that the content of the file is
        // corrupt.
        boolean forceRegeneration = AndroidGradleOptions.refreshExternalNativeModel(project);

        if (ExternalNativeBuildTaskUtils.shouldRegenerateOutOfDateJsons(project)) {
            ThreadRecorder.get().record(ExecutionType.VARIANT_MANAGER_EXTERNAL_NATIVE_CONFIG_VALUES,
                    project.getPath(), null,
                    new Recorder.Block<Void>() {
                        @Override
                        public Void call() throws Exception {
                            for (BaseVariantData variantData : variantManager
                                    .getVariantDataList()) {
                                ExternalNativeJsonGenerator generator =
                                        variantData.getScope().getExternalNativeJsonGenerator();
                                if (generator != null) {
                                    // This will generate any out-of-date or non-existent JSONs.
                                    // When refreshExternalNativeModel() is true it will also
                                    // force update all JSONs.
                                    generator.build(forceRegeneration);

                                    variantData.getScope().addExternalNativeBuildConfigValues(
                                            generator.readExistingNativeBuildConfigurations());
                                }
                            }
                            return null;
                        }
                    });
        }
    }

    private boolean isVerbose() {
        return project.getLogger().isEnabled(LogLevel.INFO);
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo();
        if (targetInfo == null) {
            if (extension.getCompileOptions() == null) {
                throw new GradleException("Calling getBootClasspath before compileSdkVersion");
            }

            sdkHandler.initTarget(
                    extension.getCompileSdkVersion(),
                    extension.getBuildToolsRevision(),
                    extension.getLibraryRequests(),
                    androidBuilder,
                    SdkHandler.useCachedSdk(project));
        }
    }

    /**
     * Check the sub-projects structure :
     * So far, checks that 2 modules do not have the same identification (group+name).
     */
    private void checkModulesForErrors() {
        Project rootProject = project.getRootProject();
        Map<String, Project> subProjectsById = new HashMap<String, Project>();
        for (Project subProject : rootProject.getAllprojects()) {
            String id = subProject.getGroup().toString() + ":" + subProject.getName();
            if (subProjectsById.containsKey(id)) {
                String message = String.format(
                        "Your project contains 2 or more modules with the same " +
                                "identification %1$s\n" +
                                "at \"%2$s\" and \"%3$s\".\n" +
                                "You must use different identification (either name or group) for " +
                                "each modules.",
                        id,
                        subProjectsById.get(id).getPath(),
                        subProject.getPath() );
                throw new StopExecutionException(message);
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    /**
     * Verify the plugin version.  If a newer version of gradle-experimental plugin is applied, then
     * builder.jar module will be resolved to a different version than the one this gradle plugin is
     * compiled with.  Throw an error and suggest to update this plugin.
     */
    private static void checkPluginVersion() {
        String actualGradlePluginVersion = Version.getAndroidGradlePluginVersion();
        if(!actualGradlePluginVersion.equals(
                com.android.build.gradle.internal.Version.ANDROID_GRADLE_PLUGIN_VERSION)) {
            throw new UnsupportedVersionException(String.format("Plugin version mismatch.  "
                    + "'com.android.tools.build:gradle-experimental:%s' was applied, and it "
                    + "requires 'com.android.tools.build:gradle:%s'.  Current version is '%s'.  "
                    + "Please update to version '%s'.",
                    Version .getAndroidGradleComponentPluginVersion(),
                    Version .getAndroidGradlePluginVersion(),
                    com.android.build.gradle.internal.Version.ANDROID_GRADLE_PLUGIN_VERSION,
                    Version .getAndroidGradlePluginVersion()));
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (AndroidGradleOptions.overridePathCheck(project)) {
            return;
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ASCII.matchesAllOf(project.getRootDir().getAbsolutePath())) {
            return;
        }

        String message =
                "Your project path contains non-ASCII characters. This will most likely "
                        + "cause the build to fail on Windows. Please move your project to a different "
                        + "directory. See http://b.android.com/95744 for details. "
                        + "This warning can be disabled by adding the line '"
                        + AndroidGradleOptions.OVERRIDE_PATH_CHECK_PROPERTY
                        + "=true' to gradle.properties file in the project directory.";

        throw new StopExecutionException(message);
    }

    @NonNull
    @Override
    public ToolingModelBuilderRegistry getModelBuilderRegistry() {
        return registry;
    }

    private static SettingsController getSettingsController() {
        return new SettingsController() {
            @Override
            public boolean getForceHttp() {
                return false;
            }

            @Override
            public void setForceHttp(boolean force) {
                // Default, doesn't allow to set force HTTP.
            }

            @Nullable
            @Override
            public Channel getChannel() {
                return Channel.DEFAULT;
            }
        };
    }

    private static Downloader getDownloader() {
        return new LocalFileAwareDownloader(new LegacyDownloader(FileOpUtils.create()));
    }
}
