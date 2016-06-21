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

package com.android.build.gradle.integration.common.fixture;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.performance.BenchmarkMode;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Version;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JUnit4 test rule for integration test.
 *
 * This rule create a gradle project in a temporary directory.
 * It can be use with the @Rule or @ClassRule annotations.  Using this class with @Rule will create
 * a gradle project in separate directories for each unit test, whereas using it with @ClassRule
 * creates a single gradle project.
 *
 * The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
public class GradleTestProject implements TestRule {

    public static final File TEST_RES_DIR = new File("src/test/resources");
    public static final File TEST_PROJECT_DIR = new File("test-projects");

    public static final int DEFAULT_COMPILE_SDK_VERSION = 23;
    public static final int LATEST_NDK_PLATFORM_VERSION = 21;

    /**
     * Last SDK that contained java 6 bytecode in the platform jar. Since we run integration tests
     * on Java 6, this is needed to cover unit testing support.
     */
    public static final int LAST_JAVA6_SDK_VERSION = 19;

    public static final String DEFAULT_BUILD_TOOL_VERSION;
    public static final String UPCOMING_BUILD_TOOL_VERSION = "24.0.0";
    public static final String REMOTE_TEST_PROVIDER = System.getenv().get("REMOTE_TEST_PROVIDER");

    public static final String DEVICE_PROVIDER_NAME = REMOTE_TEST_PROVIDER != null ?
            REMOTE_TEST_PROVIDER : BuilderConstants.CONNECTED;

    public static final String GRADLE_TEST_VERSION = "2.10";
    public static final String GRADLE_EXP_TEST_VERSION = "2.11";

    public static final String ANDROID_GRADLE_PLUGIN_VERSION;

    public static final boolean USE_JACK;


    private static final String RECORD_BENCHMARK_NAME = "com.android.benchmark.name";
    private static final String RECORD_BENCHMARK_MODE = "com.android.benchmark.mode";
    public static final String DEVICE_TEST_TASK = "deviceCheck";

    static {
        String envBuildToolVersion = System.getenv("CUSTOM_BUILDTOOLS");
        DEFAULT_BUILD_TOOL_VERSION = !Strings.isNullOrEmpty(envBuildToolVersion) ?
                envBuildToolVersion : "23.0.2";
        String envVersion = System.getenv().get("CUSTOM_PLUGIN_VERSION");
        ANDROID_GRADLE_PLUGIN_VERSION = !Strings.isNullOrEmpty(envVersion) ? envVersion
                : Version.ANDROID_GRADLE_PLUGIN_VERSION;
        String envJack = System.getenv().get("CUSTOM_JACK");
        USE_JACK = !Strings.isNullOrEmpty(envJack);
    }

    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_LOCAL_REPO = "commonLocalRepo.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_GRADLE_PLUGIN_VERSION = "commonGradlePluginVersion.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    public static class Builder {

        @Nullable
        private String name;
        @Nullable
        private TestProject testProject = null;
        @Nullable
        private File sdkDir = SdkHelper.findSdkDir();
        @Nullable
        private File ndkDir = findNdkDir();
        @Nullable
        private String targetGradleVersion;
        private boolean useJack = USE_JACK;
        private boolean useMinify = false;
        @NonNull
        private List<String> gradleProperties = Lists.newArrayList();
        @Nullable
        private String heapSize;

        /**
         * Create a GradleTestProject.
         */
        public GradleTestProject create() {
            if (targetGradleVersion == null) {
                targetGradleVersion = GRADLE_TEST_VERSION;
            }
            return new GradleTestProject(
                    name,
                    testProject,
                    useMinify,
                    useJack,
                    targetGradleVersion,
                    sdkDir,
                    ndkDir,
                    gradleProperties,
                    heapSize);
        }

        /**
         * Set the name of the project.
         *
         * Necessary if you have multiple projects in a test class.
         */
        public Builder withName(@NonNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Use the gradle version for experimental plugin.
         */
        public Builder useExperimentalGradleVersion(boolean mode) {
            if (mode) {
                targetGradleVersion = GRADLE_EXP_TEST_VERSION;
            }
            return this;
        }

        /**
         * Create a project without setting ndk.dir in local.properties.
         */
        public Builder withoutNdk() {
            this.ndkDir = null;
            return this;
        }

        /**
         * Create GradleTestProject from a TestProject.
         */
        public Builder fromTestApp(@NonNull TestProject testProject) {
            this.testProject = testProject;
            return this;
        }

        /**
         * Create GradleTestProject from an existing test project.
         */
        public Builder fromTestProject(@NonNull String project) {
            AndroidTestApp app = new EmptyTestApp();
            name = project;
            File projectDir = new File(TEST_PROJECT_DIR, project);
            addAllFiles(app, projectDir);
            return fromTestApp(app);
        }

        /**
         * Create GradleTestProject from an existing test project.
         */
        public Builder fromExternalProject(@NonNull String project) {
            try {
                AndroidTestApp app = new EmptyTestApp();
                name = project;
                // compute the root folder of the checkout, based on test-projects.
                File parentDir = TEST_PROJECT_DIR.getCanonicalFile().getParentFile().getParentFile()
                        .getParentFile().getParentFile().getParentFile();
                parentDir = new File(parentDir, "external");
                File projectDir = new File(parentDir, project);
                addAllFiles(app, projectDir);
                return fromTestApp(app);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Add a new file to the project.
         */
        public Builder addFile(@NonNull TestSourceFile file) {
            return addFiles(Lists.newArrayList(file));
        }

        /**
         * Add a new file to the project.
         */
        public Builder addFiles(@NonNull List<TestSourceFile> files) {
            if (!(this.testProject instanceof AndroidTestApp)) {
                throw new IllegalStateException("addFile is only for AndroidTestApp");
            }
            AndroidTestApp app = (AndroidTestApp) this.testProject;
            for (TestSourceFile file : files) {
                app.addFile(file);
            }
            return this;
        }

        /**
         * Add gradle properties.
         */
        public Builder addGradleProperties(@NonNull String property) {
            gradleProperties.add(property);
            return this;
        }

        /**
         * Sets the test heap size requirement. Example values : 1024m, 2048m...
         *
         * @param heapSize the heap size in a format understood by the -Xmx JVM parameter
         * @return itself.
         */
        public Builder withHeap(String heapSize) {
            this.heapSize = heapSize;
            return this;
        }

        public Builder withJack(boolean useJack) {
            this.useJack = useJack;
            return this;
        }

        public Builder withMinify(boolean useMinify) {
            this.useMinify = useMinify;
            return this;
        }

        private static class EmptyTestApp extends AbstractAndroidTestApp {
            @Override
            public boolean containsFullBuildScript() {
                return true;
            }
        }
    }

    private final String name;
    private final File outDir;
    @Nullable
    private File testDir;
    private File sourceDir;
    private File buildFile;
    private File localProp;
    private final File ndkDir;
    private final File sdkDir;


    private final Collection<String> gradleProperties;

    @Nullable
    private final TestProject testProject;

    private final String targetGradleVersion;

    private final boolean useJack;
    private final boolean minifyEnabled;

    @Nullable
    private String heapSize;

    private GradleBuildResult lastBuild;
    private ProjectConnection projectConnection;
    private final GradleTestProject rootProject;
    private final List<ProjectConnection> openConnections;

    private GradleTestProject(
            @Nullable String name,
            @Nullable TestProject testProject,
            boolean minifyEnabled,
            boolean useJack,
            String targetGradleVersion,
            @Nullable File sdkDir,
            @Nullable File ndkDir,
            @NonNull Collection<String> gradleProperties,
            @Nullable String heapSize) {
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        testDir = null;
        buildFile = sourceDir = null;
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.minifyEnabled = minifyEnabled;
        this.useJack = useJack;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        this.sdkDir = sdkDir;
        this.ndkDir = ndkDir;
        this.heapSize = heapSize;
        this.gradleProperties = gradleProperties;
        openConnections = Lists.newArrayList();
        rootProject = this;
    }

    /**
     * Create a GradleTestProject representing a subProject of another GradleTestProject.
     * @param subProject name of the subProject.
     * @param rootProject root GradleTestProject.
     */
    private GradleTestProject(
            @NonNull String subProject,
            @NonNull GradleTestProject rootProject) {
        name = subProject;
        outDir = rootProject.outDir;

        testDir = new File(rootProject.testDir, subProject);
        assertTrue("No subproject dir at " + testDir.toString(), testDir.isDirectory());

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");
        ndkDir = rootProject.ndkDir;
        sdkDir = rootProject.sdkDir;
        gradleProperties = ImmutableList.of();
        testProject = null;
        targetGradleVersion = rootProject.getTargetGradleVersion();
        minifyEnabled = false;
        useJack = false;
        openConnections = null;
        this.rootProject = rootProject;
    }

    private String getTargetGradleVersion() {
        return targetGradleVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Recursively delete directory or file.
     *
     * @param root directory to delete
     */
    private static void deleteRecursive(File root) {
        if (root.exists()) {
            if (root.isDirectory()) {
                File files[] = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteRecursive(file);
                    }
                }
            }
            assertTrue("Failed to delete " + root.getAbsolutePath(), root.delete());
        }
    }

    /**
     * Add all files in a directory to an AndroidTestApp.
     */
    private static void addAllFiles(AndroidTestApp app, File projectDir) {
        for (String filePath : TestFileUtils.listFiles(projectDir)) {
            File file = new File(filePath);
            try {
                app.addFile(
                        new TestSourceFile(
                                file.getParent(),
                                file.getName(),
                                Files.toByteArray(new File(projectDir, filePath))));
            } catch (IOException e) {
                fail(e.toString());
            }
        }
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createTestDirectory(description.getTestClass(), description.getMethodName());
                try {
                    base.evaluate();
                } finally {
                    openConnections.forEach(ProjectConnection::close);
                }
            }
        };
    }

    private void createTestDirectory(Class<?> testClass, String methodName)
            throws IOException, StreamException {
        // On windows, move the temporary copy as close to root to avoid running into path too
        // long exceptions.
        testDir = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
                ? new File(new File(new File(System.getProperty("user.home")), "android-tests"),
                testClass.getSimpleName())
                : new File(outDir, testClass.getSimpleName());

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (methodName != null) {
            String dirName = methodName;
            dirName = dirName.replaceAll("[^a-zA-Z0-9_]", "_");
            testDir = new File(testDir, dirName);
        }
        testDir = new File(testDir, name);

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");

        if (testDir.exists()) {
            deleteRecursive(testDir);
        }
        FileUtils.mkdirs(testDir);
        FileUtils.mkdirs(sourceDir);

        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_HEADER),
                new File(testDir.getParent(), COMMON_HEADER));
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_LOCAL_REPO),
                new File(testDir.getParent(), COMMON_LOCAL_REPO));
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT),
                new File(testDir.getParent(), COMMON_BUILD_SCRIPT));
        Files.copy(
                new File(TEST_PROJECT_DIR, COMMON_GRADLE_PLUGIN_VERSION),
                new File(testDir.getParent(), COMMON_GRADLE_PLUGIN_VERSION));

        if (testProject != null) {
            testProject.write(
                    testDir,
                    testProject.containsFullBuildScript() ? "" :getGradleBuildscript());
        } else {
            Files.write(
                    getGradleBuildscript(),
                    buildFile,
                    Charsets.UTF_8);
        }

        localProp = createLocalProp(testDir, sdkDir, ndkDir);
        createGradleProp();
    }

    /**
     * Create a GradleTestProject representing a subproject.
     */
    public GradleTestProject getSubproject(String name) {
        if (name.startsWith(":")) {
            name = name.substring(1);
        }
        return new GradleTestProject(name, rootProject);
    }

    /**
     * Return the name of the test project.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the directory containing the test project.
     */
    public File getTestDir() {
        return testDir;
    }

    /**
     * Return the build.gradle of the test project.
     */
    public File getSettingsFile() {
        return new File(testDir, "settings.gradle");
    }

    /**
     * Return the build.gradle of the test project.
     */
    public File getBuildFile() {
        return buildFile;
    }

    /**
     * Change the build file used for execute.  Should be run after @Before/@BeforeClass.
     */
    public void setBuildFile(@Nullable String buildFileName) {
        Preconditions.checkNotNull(
                buildFile,
                "Cannot call selectBuildFile before test directory is created.");
        if (buildFileName == null) {
            buildFileName = "build.gradle";
        }
        buildFile = new File(testDir, buildFileName);
        assertThat(buildFile).exists();
    }


    /**
     * Return the output directory from Android plugins.
     */
    public File getOutputDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_OUTPUTS));
    }

    /**
     * Return the output directory from Android plugins.
     */
    public File getIntermediatesDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_INTERMEDIATES));
    }

    /**
     * Return a File under the output directory from Android plugins.
     */
    public File getOutputFile(String path) {
        return new File(getOutputDir(), path);
    }

    /**
     * Return the output apk File from the application plugin for the given dimension.
     *
     * Expected dimensions orders are:
     *   - product flavors
     *   - build type
     *   - other modifiers (e.g. "unsigned", "aligned")
     */
    public File getApk(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile(
                "apk/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ANDROID_PACKAGE);
    }

    public File getTestApk(RunGradleTasks.Packaging packaging, String... dimensions) {
        List<String> dimensionList = Lists.newArrayList(dimensions);
        dimensionList.add("androidTest");
        if (packaging == RunGradleTasks.Packaging.OLD_PACKAGING) {
            dimensionList.add("unaligned");
        }

        return getApk(Iterables.toArray(dimensionList, String.class));
    }

    public File getTestApk(String... dimensions) {
        @SuppressWarnings("ConstantConditions")
        RunGradleTasks.Packaging packaging =
                AndroidGradleOptions.DEFAULT_USE_OLD_PACKAGING
                        ? RunGradleTasks.Packaging.OLD_PACKAGING
                        : RunGradleTasks.Packaging.NEW_PACKAGING;

        return getTestApk(packaging, dimensions);
    }

    /**
     * Return the output aar File from the library plugin for the given dimension.
     *
     * Expected dimensions orders are:
     *   - product flavors
     *   - build type
     *   - other modifiers (e.g. "unsigned", "aligned")
     */
    public File getAar(String... dimensions) {
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile("aar/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_AAR);
    }

    /**
     * Returns the SDK dir
     */
    public File getSdkDir() {
        return sdkDir;
    }

    /**
     * Returns the NDK dir
     */
    public File getNdkDir() {
        return ndkDir;
    }

    /**
     * Returns a string that contains the gradle buildscript content
     */
    public static String getGradleBuildscript() {
        return "apply from: \"../commonHeader.gradle\"\n" +
               "buildscript { apply from: \"../commonBuildScript.gradle\" }\n" +
               "\n" +
               "apply from: \"../commonLocalRepo.gradle\"\n";
    }

    /** Fluent method to run a build. */
    public RunGradleTasks executor() {
        return new RunGradleTasks(this, getProjectConnection());
    }

    /** Fluent method to get the model. */
    public BuildModel model() {
        return new BuildModel(this, getProjectConnection());
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(@NonNull String... tasks) {
        lastBuild = executor().run(tasks);
    }


    public void execute(@NonNull List<String> arguments, @NonNull String... tasks) {
        lastBuild = executor().withArguments(arguments).run(tasks);
    }

    public void executeWithBenchmark(
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode,
            @NonNull String... tasks) {
        lastBuild = executor().recordBenchmark(benchmarkName, benchmarkMode).run(tasks);
    }

    public GradleConnectionException executeExpectingFailure(@NonNull String... tasks) {
        lastBuild =  executor().expectFailure().run(tasks);
        return lastBuild.getException();
    }

    public GradleConnectionException executeExpectingFailure(
            @NonNull final List<String> arguments,
            @NonNull String... tasks) {
        lastBuild = executor().expectFailure().withArguments(arguments).run(tasks);
        return lastBuild.getException();
    }

    public void executeConnectedCheck() {
        lastBuild = executor().executeConnectedCheck();
    }

    public void executeConnectedCheck(@NonNull List<String> arguments) {
        lastBuild = executor().withArguments(arguments).executeConnectedCheck();
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public AndroidProject executeAndReturnModel(@NonNull String... tasks) {
        lastBuild = executor().run(tasks);
        return model().getSingle();
    }

    /**
     * Runs gradle on the project, and returns the model of the specified type.
     * Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model for the project with the specified type.
     */
    @NonNull
    public <T> T executeAndReturnModel(Class<T> modelClass, String... tasks) {
        lastBuild = executor().run(tasks);
        return model().getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public AndroidProject executeAndReturnModel(int modelLevel, String... tasks) {
        lastBuild = executor().run(tasks);
        return model().level(modelLevel).getSingle();
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param modelLevel whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public <T> T executeAndReturnModel(
            Class<T> modelClass,
            int modelLevel,
            String... tasks) {
        lastBuild = executor().run(tasks);
        return model().level(modelLevel).getSingle(modelClass);
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project.
     * Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public Map<String, AndroidProject> executeAndReturnMultiModel(String... tasks) {
        lastBuild = executor().run(tasks);
        return model().getMulti();
    }

    /**
     * Return the stdout from the last execute command.
     *
     * @deprecated  use {@link GradleBuildResult#getStdout()} ()} instead.
     */
    @Deprecated
    public String getStdout() {
        return lastBuild.getStdout();
    }

    /**
     * Return the stderr from the last execute command.
     *
     * @deprecated  use {@link GradleBuildResult#getStderr()} instead.
     */
    @Deprecated
    @NonNull
    public String getStderr() {
        return lastBuild.getStderr();
    }

    /**
     * Create a File object.  getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file.  May be a relative path.
     */
    public File file(String path) {
        File result = new File(FileUtils.toSystemDependentPath(path));
        if (result.isAbsolute()) {
            return result;
        } else {
            return new File(testDir, path);
        }
    }

    /**
     * Returns the NDK folder as built from the Android source tree.
     */
    private static File findNdkDir() {
        String androidHome = System.getenv("ANDROID_NDK_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find NDK in ANDROID_NDK_HOME=" + androidHome);
            }
        }
        return null;
    }

    /**
     * Returns a Gradle project Connection
     */
    @NonNull
    private ProjectConnection getProjectConnection() {
        if (projectConnection != null) {
            return projectConnection;
        }
        GradleConnector connector = GradleConnector.newConnector();

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        ((DefaultGradleConnector) connector).daemonMaxIdleTime(10, TimeUnit.SECONDS);

        projectConnection = connector
                .useGradleVersion(targetGradleVersion)
                .forProjectDirectory(testDir)
                .connect();

        rootProject.openConnections.add(projectConnection);

        return projectConnection;
    }

    private static File createLocalProp(
            @NonNull File project,
            @NonNull File sdkDir,
            @Nullable File ndkDir) throws IOException, StreamException {
        ProjectPropertiesWorkingCopy localProp = ProjectProperties.create(
                project.getAbsolutePath(), ProjectProperties.PropertyType.LOCAL);
        localProp.setProperty(ProjectProperties.PROPERTY_SDK, sdkDir.getAbsolutePath());
        if (ndkDir != null) {
            localProp.setProperty(ProjectProperties.PROPERTY_NDK, ndkDir.getAbsolutePath());
        }
        localProp.save();

        return (File) localProp.getFile();
    }

    private void createGradleProp() throws IOException {
        if (gradleProperties.isEmpty()) {
            return;
        }
        File propertyFile = file("gradle.properties");
        Files.write(Joiner.on('\n').join(gradleProperties), propertyFile, Charset.defaultCharset());
    }

    @Nullable
    String getHeapSize() {
        return heapSize;
    }

    boolean isUseJack() {
        return useJack;
    }

    boolean isMinifyEnabled() {
        return minifyEnabled;
    }

    public File getLocalProp() {
        return localProp;
    }

}
