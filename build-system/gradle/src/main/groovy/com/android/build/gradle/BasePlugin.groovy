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

package com.android.build.gradle

import com.android.annotations.Nullable
import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.DependencyManager
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LibraryCache
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.TaskContainerAdaptor
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.BuildTypeFactory
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.GroupableProductFlavorFactory
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.SigningConfigFactory
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.SpanRecorders
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.tasks.JillTask
import com.android.build.gradle.tasks.PreDex
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.DefaultBuildType
import com.android.builder.internal.compiler.JackConversionCache
import com.android.builder.internal.compiler.PreDexCache
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.ProcessRecorderFactory
import com.android.builder.profile.ThreadRecorder
import com.android.builder.sdk.TargetInfo
import com.android.ide.common.blame.output.BlameAwareLoggedProcessOutputHandler
import com.android.ide.common.internal.ExecutorSingleton
import com.android.utils.ILogger
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.BuildException
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import java.security.MessageDigest
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.regex.Pattern

import static com.android.builder.core.BuilderConstants.DEBUG
import static com.android.builder.core.BuilderConstants.RELEASE
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static java.io.File.separator

/**
 * Base class for all Android plugins
 */
@CompileStatic
public abstract class BasePlugin {

    private static final String GRADLE_MIN_VERSION = "2.2"
    public static final Pattern GRADLE_ACCEPTABLE_VERSIONS = Pattern.compile("2\\.[2-9].*")
    private static final String GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY =
            "com.android.build.gradle.overrideVersionCheck"

    // default retirement age in days since its inception date for RC or beta versions.
    private static final int DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE = 40

    protected BaseExtension extension

    protected VariantManager variantManager

    protected TaskManager taskManager

    protected Project project

    protected SdkHandler sdkHandler

    protected AndroidBuilder androidBuilder

    protected Instantiator instantiator

    private ToolingModelBuilderRegistry registry

    private JacocoPlugin jacocoPlugin

    private LoggerWrapper loggerWrapper

    private ExtraModelInfo extraModelInfo

    private String creator

    private boolean hasCreatedTasks = false

    protected BasePlugin(Instantiator instantiator, ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
        this.registry = registry
        String pluginVersion = getLocalVersion()
        if (pluginVersion != null) {
            creator = "Android Gradle " + pluginVersion
        } else  {
            creator = "Android Gradle"
        }
        verifyRetirementAge()
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

        String inceptionDateAttr = manifest.mainAttributes.getValue("Inception-Date")
        // when running in unit tests, etc... the manifest entries are absent.
        if (inceptionDateAttr == null) {
            return;
        }
        def items = inceptionDateAttr.split(':')
        GregorianCalendar inceptionDate = new GregorianCalendar(Integer.parseInt(items[0]),
                Integer.parseInt(items[1]), Integer.parseInt(items[2]));

        int retirementAge = getRetirementAge(manifest.mainAttributes.getValue("Plugin-Version"))

        if (retirementAge == -1) {
            return;
        }
        Calendar now = GregorianCalendar.getInstance()
        int days = now.minus(inceptionDate)
        if (days > retirementAge) {
            // this plugin is too old.
            String dailyOverride = System.getenv("ANDROID_DAILY_OVERRIDE")
            MessageDigest cript = MessageDigest.getInstance("SHA-1")
            cript.reset()
            // encode the day, not the current time.
            cript.update(
                    "${now.get(Calendar.YEAR)}:${now.get(Calendar.MONTH)}:${now.get(Calendar.DATE)}"
                            .getBytes("utf8"))
            String overrideValue = new BigInteger(1, cript.digest()).toString(16)
            if (dailyOverride == null) {
                String message = """
                    Plugin is too old, please update to a more recent version,
                    or set ANDROID_DAILY_OVERRIDE environment variable to
                    \"${overrideValue}\""""
                System.err.println(message)
                throw new RuntimeException(message)
            } else {
                if (!dailyOverride.equals(overrideValue)) {
                    String message = """
                    Plugin is too old and ANDROID_DAILY_OVERRIDE value is
                    also outdated, please use new value :
                    \"${overrideValue}\""""
                    System.err.println(message)
                    throw new RuntimeException(message)
                }
            }
        }
    }

    private static int getRetirementAge(@Nullable String version) {
        if (version == null || version.contains("rc") || version.contains("beta")
                || version.contains("alpha")) {
            return DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE
        }
        return -1;
    }

    protected abstract Class<? extends BaseExtension> getExtensionClass()
    protected abstract VariantFactory getVariantFactory()
    protected abstract TaskManager createTaskManager(
            Project project,
            AndroidBuilder androidBuilder,
            BaseExtension extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry)

    /**
     * Return whether this plugin creates Android library.  Should be overridden if true.
     */
    protected boolean isLibrary() {
        return false;
    }

    @VisibleForTesting
    VariantManager getVariantManager() {
        return variantManager
    }

    protected ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }


    protected void apply(Project project) {
        this.project = project
        ProcessRecorderFactory.initialize(logger, project.rootProject.
                file("profiler" + System.currentTimeMillis() + ".json"))
        project.gradle.addListener(new RecordingBuildListener(ThreadRecorder.get()));

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE) {
            configureProject()
        }

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSTION_CREATION) {
            createExtension()
        }

        SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION) {
            createTasks()
        }
    }

    protected void configureProject() {
        checkGradleVersion()
        extraModelInfo = new ExtraModelInfo(project)
        sdkHandler = new SdkHandler(project, logger)
        androidBuilder = new AndroidBuilder(
                project == project.rootProject ? project.name : project.path,
                creator,
                new GradleProcessExecutor(project),
                new GradleJavaProcessExecutor(project),
                new BlameAwareLoggedProcessOutputHandler(getLogger(),
                        extraModelInfo.getErrorFormatMode()),
                logger,
                verbose)

        project.apply plugin: JavaBasePlugin

        project.apply plugin: JacocoPlugin
        jacocoPlugin = project.plugins.getPlugin(JacocoPlugin)

        project.tasks.getByName("assemble").description =
                "Assembles all variants of all applications and secondary packages."

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        project.gradle.buildFinished {
            ExecutorSingleton.shutdown()
            sdkHandler.unload()
            SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_BUILD_FINISHED) {
                PreDexCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"),
                        logger)
                JackConversionCache.getCache().clear(
                        project.rootProject.file(
                                "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"),
                        logger)
                LibraryCache.getCache().unload()
            }
            ProcessRecorderFactory.shutdown();
        }

        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            for (Task task : taskGraph.allTasks) {
                if (task instanceof PreDex) {
                    PreDexCache.getCache().load(
                            project.rootProject.file(
                                    "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/dex-cache/cache.xml"))
                    break;
                } else if (task instanceof JillTask) {
                    JackConversionCache.getCache().load(
                            project.rootProject.file(
                                    "${project.rootProject.buildDir}/${FD_INTERMEDIATES}/jack-cache/cache.xml"))
                    break;
                }
            }
        }
    }

    private void createExtension() {
        def buildTypeContainer = project.container(BuildType,
                new BuildTypeFactory(instantiator, project, project.getLogger()))
        def productFlavorContainer = project.container(GroupableProductFlavor,
                new GroupableProductFlavorFactory(instantiator, project, project.getLogger()))
        def signingConfigContainer = project.container(SigningConfig,
                new SigningConfigFactory(instantiator))

        extension = project.extensions.create('android', getExtensionClass(),
                (ProjectInternal) project, instantiator, androidBuilder, sdkHandler,
                buildTypeContainer, productFlavorContainer, signingConfigContainer,
                extraModelInfo, isLibrary())

        // create the default mapping configuration.
        project.configurations.create("default-mapping").description = "Configuration for default mapping artifacts."
        project.configurations.create("default-metadata").description = "Metadata for the produced APKs."

        DependencyManager dependencyManager = new DependencyManager(project, extraModelInfo)
        taskManager = createTaskManager(
                project,
                androidBuilder,
                extension,
                sdkHandler,
                dependencyManager,
                registry)

        VariantFactory variantFactory = getVariantFactory()
        variantManager = new VariantManager(
                project,
                androidBuilder,
                extension,
                variantFactory,
                taskManager,
                instantiator)

        // Register a builder for the custom tooling model
        ModelBuilder modelBuilder = new ModelBuilder(
                androidBuilder, variantManager, taskManager, extension, extraModelInfo, isLibrary())
        registry.register(modelBuilder);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded { SigningConfig signingConfig ->
            variantManager.addSigningConfig((SigningConfig) signingConfig)
        }

        buildTypeContainer.whenObjectAdded { DefaultBuildType buildType ->
            variantManager.addBuildType((BuildType) buildType)
        }

        productFlavorContainer.whenObjectAdded { GroupableProductFlavor productFlavor ->
            variantManager.addProductFlavor(productFlavor)
        }

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing signingConfigs is not supported.")
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not supported.")
        }
        productFlavorContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not supported.")
        }

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(buildTypeContainer, productFlavorContainer, signingConfigContainer)
    }

    private void createTasks() {
        SpanRecorders.record(project, ExecutionType.TASK_MANAGER_CREATE_TASKS) {
            taskManager.createTasksBeforeEvaluate(new TaskContainerAdaptor(project.getTasks()))
        }

        project.afterEvaluate {
            ensureTargetSetup()
            SpanRecorders.record(project, ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS) {
                createAndroidTasks(false)
            }
        }
    }

    private void checkGradleVersion() {
        if (!GRADLE_ACCEPTABLE_VERSIONS.matcher(project.getGradle().gradleVersion).matches()) {
            boolean allowNonMatching = Boolean.getBoolean(GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY)
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage = String.format(
                "Gradle version %s is required. Current version is %s. " +
                "If using the gradle wrapper, try editing the distributionUrl in %s " +
                "to gradle-%s-all.zip",
                GRADLE_MIN_VERSION, project.getGradle().gradleVersion, file.getAbsolutePath(),
                GRADLE_MIN_VERSION);
            if (allowNonMatching) {
                getLogger().warning(errorMessage)
                getLogger().warning("As %s is set, continuing anyways.",
                        GRADLE_VERSION_CHECK_OVERRIDE_PROPERTY)
            } else {
                throw new BuildException(errorMessage, null)
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks(boolean force) {
        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.")
        }

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force
                && (!project.state.executed || project.state.failure != null)
                && SdkHandler.sTestSdkFolder == null) {
            return
        }

        if (hasCreatedTasks) {
            return
        }
        hasCreatedTasks = true

        extension.disableWrite()

        // setup SDK repositories.
        for (File file : sdkHandler.sdkLoader.repositories) {
            project.repositories.maven { MavenArtifactRepository repo ->
                repo.url = file.toURI()
            }
        }

        taskManager.createMockableJarTask()
        SpanRecorders.record(project, ExecutionType.VARIANT_MANAGER_CREATE_ANDROID_TASKS) {
            variantManager.createAndroidTasks()
        }
    }

    private boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.INFO)
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo()
        if (targetInfo == null) {
            sdkHandler.initTarget(
                    extension.getCompileSdkVersion(),
                    extension.buildToolsRevision,
                    androidBuilder)
        }
    }

    private static String getLocalVersion() {
        try {
            Class clazz = BasePlugin.class
            String className = clazz.getSimpleName() + ".class"
            String classPath = clazz.getResource(className).toString()
            if (!classPath.startsWith("jar")) {
                // Class not from JAR, unlikely
                return null
            }
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                    "/META-INF/MANIFEST.MF";

            URLConnection jarConnection = new URL(manifestPath).openConnection();
            jarConnection.setUseCaches(false);
            InputStream jarInputStream = jarConnection.getInputStream();
            Attributes attr = new Manifest(jarInputStream).getMainAttributes();
            jarInputStream.close();
            return attr.getValue("Plugin-Version");
        } catch (Throwable t) {
            return null;
        }
    }
}
