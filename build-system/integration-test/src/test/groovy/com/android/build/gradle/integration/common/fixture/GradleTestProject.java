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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.FileHelper;
import com.android.build.gradle.integration.common.utils.JacocoAgent;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Version;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final int LATEST_NDK_VERSION = 21;

    /**
     * Last SDK that contained java 6 bytecode in the platform jar. Since we run integration tests
     * on Java 6, this is needed to cover unit testing support.
     */
    public static final int LAST_JAVA6_SDK_VERSION = 19;

    public static final String DEFAULT_BUILD_TOOL_VERSION;
    public static final String REMOTE_TEST_PROVIDER = System.getenv().get("REMOTE_TEST_PROVIDER");

    public static final String DEVICE_PROVIDER_NAME = REMOTE_TEST_PROVIDER != null ?
            REMOTE_TEST_PROVIDER : BuilderConstants.CONNECTED;

    public static final String GRADLE_TEST_VERSION = "2.2.1";
    public static final String GRADLE_EXP_TEST_VERSION = "2.8";

    public static final String ANDROID_GRADLE_PLUGIN_VERSION;

    public static final String CUSTOM_JACK;
    public static final boolean USE_JACK;


    private static final String RECORD_BENCHMARK_NAME = "com.android.benchmark.name";
    private static final String RECORD_BENCHMARK_MODE = "com.android.benchmark.mode";

    public enum BenchmarkMode {
        EVALUATION, SYNC, BUILD_FULL, BUILD_INC_JAVA, BUILD_INC_RES_EDIT, BUILD_INC_RES_ADD
    }

    static {
        String envBuildToolVersion = System.getenv("CUSTOM_BUILDTOOLS");
        DEFAULT_BUILD_TOOL_VERSION = !Strings.isNullOrEmpty(envBuildToolVersion) ?
                envBuildToolVersion : "23.0.1";
        String envVersion = System.getenv().get("CUSTOM_GRADLE");
        ANDROID_GRADLE_PLUGIN_VERSION = !Strings.isNullOrEmpty(envVersion) ? envVersion
                : Version.ANDROID_GRADLE_PLUGIN_VERSION;
        String envJack = System.getenv().get("CUSTOM_JACK");
        CUSTOM_JACK = !Strings.isNullOrEmpty(envJack) ? envJack : "false";
        USE_JACK = Boolean.parseBoolean(CUSTOM_JACK);
    }

    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_LOCAL_REPO = "commonLocalRepo.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_BUILD_SCRIPT_EXP = "commonBuildScriptExperimental.gradle";
    private static final String COMMON_GRADLE_PLUGIN_VERSION = "commonGradlePluginVersion.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    public static class Builder {

        @Nullable
        private String name;

        @Nullable
        private TestProject testProject = null;

        @Nullable
        File sdkDir = SdkHelper.findSdkDir();
        @Nullable
        File ndkDir = findNdkDir();
        boolean captureStdOut = false;
        boolean captureStdErr = false;
        boolean experimentalMode = false;
        @Nullable
        private String targetGradleVersion;

        boolean useJack = false;
        boolean useMinify = false;
        @NonNull
        private List<String> gradleProperties = Lists.newArrayList();
        @Nullable
        private String heapSize;

        /**
         * Create a GradleTestProject.
         */
        public GradleTestProject create() {
            if (targetGradleVersion == null) {
                targetGradleVersion =
                        experimentalMode ? GRADLE_EXP_TEST_VERSION : GRADLE_TEST_VERSION;
            }
            return new GradleTestProject(
                    name,
                    testProject,
                    experimentalMode,
                    useMinify,
                    useJack,
                    targetGradleVersion,
                    captureStdOut,
                    captureStdErr,
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

        public Builder captureStdOut(boolean captureStdOut) {
            this.captureStdOut = captureStdOut;
            return this;
        }

        public Builder captureStdErr(boolean captureStdErr) {
            this.captureStdErr = captureStdErr;
            return this;
        }

        /**
         * Use experimental plugin for the test project.
         */
        public Builder forExperimentalPlugin(boolean mode) {
            this.experimentalMode = mode;
            return this;
        }

        /**
         * Use the gradle version for experimental plugin, but the test project do not necessarily
         * have to use experimental plugin.
         */
        public Builder useExperimentalGradleVersion(boolean mode) {
            if (mode) {
                targetGradleVersion = GRADLE_EXP_TEST_VERSION;
            }
            return this;
        }

        /**
         * Use the gradle version specified, e.g. "2.4".
         */
        public Builder useGradleVersion(String targetGradleVersion) {
            this.targetGradleVersion = targetGradleVersion;
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
            return fromTestProject(project, null);
        }

        /**
         * Create GradleTestProject from an existing test project.
         * @param buildFileSuffix if non-null, imports files named build.suffix.gradle.
         */
        public Builder fromTestProject(@NonNull String project, @Nullable String buildFileSuffix) {
            AndroidTestApp app = new EmptyTestApp();
            name = project;
            File projectDir = new File(TEST_PROJECT_DIR, project);
            addAllFiles(app, projectDir, buildFileSuffix);
            return fromTestApp(app);
        }

        /**
         * Create GradleTestProject from an existing test project.
         */
        public Builder fromExternalProject(@NonNull String project) throws IOException {
            AndroidTestApp app = new EmptyTestApp();
            name = project;
            // compute the root folder of the checkout, based on test-projects.
            File parentDir = TEST_PROJECT_DIR.getCanonicalFile().getParentFile().getParentFile()
                    .getParentFile().getParentFile().getParentFile();
            parentDir = new File(parentDir, "external");
            File projectDir = new File(parentDir, project);
            addAllFiles(app, projectDir, null /*buildFileSuffix*/);
            return fromTestApp(app);
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
    private File testDir;
    private File sourceDir;
    private File buildFile;
    private final File ndkDir;
    private final File sdkDir;

    private final ByteArrayOutputStream stdout;
    private final ByteArrayOutputStream stderr;

    private final Collection<String> gradleProperties;

    @Nullable
    private final TestProject testProject;

    private final boolean experimentalMode;
    private final String targetGradleVersion;

    private final boolean useJack;
    private final boolean minifyEnabled;

    @Nullable
    private String heapSize;

    private GradleTestProject(
            @Nullable String name,
            @Nullable TestProject testProject,
            boolean experimentalMode,
            boolean minifyEnabled,
            boolean useJack,
            String targetGradleVersion,
            boolean captureStdOut,
            boolean captureStdErr,
            @Nullable File sdkDir,
            @Nullable File ndkDir,
            @NonNull Collection<String> gradleProperties,
            @Nullable String heapSize) {
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        testDir = null;
        buildFile = sourceDir = null;
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.experimentalMode = experimentalMode;
        this.minifyEnabled = minifyEnabled;
        this.useJack = useJack;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        stdout = captureStdOut ? new ByteArrayOutputStream() : null;
        stderr = captureStdErr ? new ByteArrayOutputStream() : null;
        this.sdkDir = sdkDir;
        this.ndkDir = ndkDir;
        this.heapSize = heapSize;
        this.gradleProperties = gradleProperties;
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
        stdout = rootProject.stdout;
        stderr = rootProject.stdout;
        gradleProperties = ImmutableList.of();
        testProject = null;
        experimentalMode = rootProject.isExperimentalMode();
        targetGradleVersion = rootProject.getTargetGradleVersion();
        minifyEnabled = false;
        useJack = false;
    }

    String getTargetGradleVersion() {
        return targetGradleVersion;
    }

    boolean isExperimentalMode() {
        return experimentalMode;
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
            assertTrue(root.delete());
        }
    }

    private static final Pattern BUILD_FILE_PATTERN = Pattern.compile("build\\.(.+\\.)?gradle");

    /**
     * Add all files in a directory to an AndroidTestApp.
     */
    private static void addAllFiles(AndroidTestApp app, File projectDir, String buildFileSuffix) {
        String buildFileNameToKeep = (buildFileSuffix != null) ?
                "build." + buildFileSuffix + ".gradle" : "build.gradle";
        for (String filePath : FileHelper.listFiles(projectDir)) {
            File file = new File(filePath);
            try {
                String fileName = file.getName();
                if (BUILD_FILE_PATTERN.matcher(fileName).matches()) {
                    if (!fileName.equals(buildFileNameToKeep)) {
                        continue;
                    }
                    fileName = "build.gradle";
                }
                app.addFile(
                        new TestSourceFile(
                                file.getParent(),
                                fileName,
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
                base.evaluate();
            }
        };
    }

    public void createTestDirectory(Class<?> testClass, String methodName)
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
        assertTrue(testDir.mkdirs());
        assertTrue(sourceDir.mkdirs());

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
                new File(TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT_EXP),
                new File(testDir.getParent(), COMMON_BUILD_SCRIPT_EXP));
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

        createLocalProp(testDir, sdkDir, ndkDir);
        createGradleProp();
    }

    /**
     * Create a GradleTestProject representing a subproject.
     */
    public GradleTestProject getSubproject(String name) {
        return new GradleTestProject(name, this);
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
     * Return the directory containing the source files of the test project.
     */
    public File getSourceDir() {
        return sourceDir;
    }

    /**
     * Return the output directory from Android plugins.
     */
    public File getOutputDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_OUTPUTS));
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
    public File getApk(String ... dimensions) {
        // TODO: Add overload for tests and splits.
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile(
                "apk/" + Joiner.on("-").join(dimensionList) + SdkConstants.DOT_ANDROID_PACKAGE);
    }

    public File getTestApk(String ... dimensions) {
        String[] allDimensions = new String[dimensions.length + 2];
        System.arraycopy(dimensions, 0, allDimensions, 0, dimensions.length);
        allDimensions[allDimensions.length - 2] = "androidTest";
        allDimensions[allDimensions.length - 1] = "unaligned";
        return getApk(allDimensions);
    }

    /**
     * Return the output aar File from the library plugin for the given dimension.
     *
     * Expected dimensions orders are:
     *   - product flavors
     *   - build type
     *   - other modifiers (e.g. "unsigned", "aligned")
     */
    public File getAar(String ... dimensions) {
        // TODO: Add overload for tests and splits.
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
     * Return the directory of the repository containing the necessary plugins for testing.
     */
    private File getRepoDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        assert (source != null);
        URL location = source.getLocation();
        try {
            File dir = new File(location.toURI());
            assertTrue(dir.getPath(), dir.exists());

            File f = dir.getParentFile().getParentFile().getParentFile().getParentFile()
                    .getParentFile().getParentFile().getParentFile();
            return new File(f, "out" + File.separator + "repo");
        } catch (URISyntaxException e) {
            fail(e.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Returns a string that contains the gradle buildscript content
     */
    public String getGradleBuildscript() {
        return "apply from: \"../commonHeader.gradle\"\n" +
               "buildscript { apply from: \"../commonBuildScript" +
               (experimentalMode ? "Experimental" : "") + ".gradle\", to: buildscript }\n" +
               "\n" +
               "apply from: \"../commonLocalRepo.gradle\"\n";
    }

    /**
     * Return a list of all task names of the project.
     */
    public List<String> getTaskList() {
        ProjectConnection connection = getProjectConnection();
        try {
            GradleProject project = connection.getModel(GradleProject.class);
            List<String> tasks = Lists.newArrayList();
            for (GradleTask gradleTask : project.getTasks()) {
                tasks.add(gradleTask.getName());
            }
            return tasks;
        } finally {
            connection.close();
        }

    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(String ... tasks) {
        execute(AndroidProject.class,
                Collections.<String>emptyList(),
                false,
                false,
                ExpectedBuildResult.SUCCESS,
                tasks);
    }

    public void execute(@NonNull List<String> arguments, String ... tasks) {
        execute(AndroidProject.class,
                arguments,
                false,
                false,
                ExpectedBuildResult.SUCCESS,
                tasks);
    }

    public void executeWithBenchmark(
            @NonNull String benchmarkName,
            @NonNull BenchmarkMode benchmarkMode,
            String ... tasks) {
        List<String> arguments = ImmutableList.of(
                "-P" + RECORD_BENCHMARK_NAME + "=" + benchmarkName,
                "-P" + RECORD_BENCHMARK_MODE + "=" + benchmarkMode.name().toLowerCase(Locale.US)
        );
        execute(AndroidProject.class,
                arguments,
                false,
                false,
                ExpectedBuildResult.SUCCESS,
                tasks);
    }

    public void executeExpectingFailure(String... tasks) {
        executeExpectingFailure(Collections.<String>emptyList(), tasks);
    }

    public void executeExpectingFailure(@NonNull List<String> arguments, String... tasks) {
        execute(AndroidProject.class,
                arguments,
                false /*returnModel*/,
                false /*emulateStudio_1_0*/,
                ExpectedBuildResult.FAILURE,
                tasks);
    }

    public void executeConnectedCheck() {
        executeConnectedCheck(Collections.<String>emptyList());
    }

    public void executeConnectedCheck(List<String> arguments) {
        execute(arguments, REMOTE_TEST_PROVIDER == null ? "connectedCheck" : "deviceCheck");
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public AndroidProject executeAndReturnModel(String ... tasks) {
        return executeAndReturnModel(false, tasks);
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
    public <T> T executeAndReturnModel(Class<T> modelClass, String ... tasks) {
        return this.<T>executeAndReturnModel(modelClass, false, tasks);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public AndroidProject executeAndReturnModel(boolean emulateStudio_1_0, String ... tasks) {
        //noinspection ConstantConditions
        return execute(AndroidProject.class, Collections.<String>emptyList(), true, emulateStudio_1_0,
                ExpectedBuildResult.SUCCESS, tasks);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param modelClass Class of the model to return
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public <T> T executeAndReturnModel(
            Class<T> modelClass,
            boolean emulateStudio_1_0,
            String ... tasks) {
        //noinspection ConstantConditions
        return execute(modelClass, Collections.<String>emptyList(), true, emulateStudio_1_0,
                ExpectedBuildResult.SUCCESS, tasks);
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
    public Map<String, AndroidProject> executeAndReturnMultiModel(String ... tasks) {
        return executeAndReturnMultiModel(false, tasks);
    }

    /**
     * Runs gradle on the project, and returns a project model for each sub-project.
     * Throws exception on failure.
     *
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    @NonNull
    public Map<String, AndroidProject> executeAndReturnMultiModel(boolean emulateStudio_1_0, String ... tasks) {
        ProjectConnection connection = getProjectConnection();
        try {
            executeBuild(Collections.<String>emptyList(), connection, tasks,
                    ExpectedBuildResult.SUCCESS);

            return buildModel(
                    connection,
                    new GetAndroidModelAction<AndroidProject>(AndroidProject.class),
                    emulateStudio_1_0,
                    null,
                    null);

        } finally {
            connection.close();
        }
    }

    /**
     * Returns the project model without building.
     *
     * This will fail if the project is a multi-project setup or if there are any sync issues
     * while loading the project.
     */
    @NonNull
    public AndroidProject getSingleModel() {
        return getSingleModel(false /* emulateStudio_1_0 */, true /*assertNodSyncIssues */);
    }

    /**
     * Returns the project model without building, querying it the way Studio 1.0 does.
     *
     * This will fail if the project is a multi-project setup or if there are any sync issues
     * while loading the project.
     */
    @NonNull
    public AndroidProject getSingleModelAsStudio1() {
        return getSingleModel(true /* emulateStudio_1_0 */, true /*assertNodSyncIssues */);
    }

    /**
     * Returns the project model without building.
     *
     * This will fail if the project is a multi-project setup.
     */
    @NonNull
    public AndroidProject getSingleModelIgnoringSyncIssues() {
        return getSingleModel(false /* emulateStudio_1_0 */, false /*assertNodSyncIssues */);
    }

    /**
     * Returns the project model without building, querying it the way Studio 1.0 does.
     *
     * This will fail if the project is a multi-project setup.
     */
    @NonNull
    public AndroidProject getSingleModelIgnoringSyncIssuesAsStudio1() {
        return getSingleModel(true /* emulateStudio_1_0 */, false /*assertNodSyncIssues */);
    }

    /**
     * Returns the project model without building.
     *
     * This will fail if the project is a multi-project setup.
     *
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param assertNoSyncIssues true if the presence of sync issues during the model evaluation
     *                           should raise a {@link AssertionError}s
     */
    @NonNull
    private AndroidProject getSingleModel(boolean emulateStudio_1_0, boolean assertNoSyncIssues) {
        ProjectConnection connection = getProjectConnection();
        try {
            Map<String, AndroidProject> modelMap = buildModel(
                    connection,
                    new GetAndroidModelAction<AndroidProject>(AndroidProject.class),
                    emulateStudio_1_0,
                    null,
                    null);

            // ensure there was only one project
            assertEquals("Quering GradleTestProject.getModel() with multi-project settings",
                    1, modelMap.size());

            AndroidProject androidProject = modelMap.get(":");
            if (assertNoSyncIssues) {
                assertNoSyncIssues(androidProject);
            }
            return androidProject;
        } finally {
            connection.close();
        }
    }

    /**
     * Returns a project model for each sub-project without building generating a
     * {@link AssertionError} if any sync issue is raised during the model loading.
     */
    @NonNull
    public Map<String, AndroidProject> getAllModels() {
        return getAllModelsWithBenchmark(null, null);
    }

    /**
     * Returns a project model for each sub-project without building generating a
     * @param benchmarkName optional benchmark name to pass to Gradle
     * @param benchmarkMode optional benchmark mode to pass to gradle.

     * {@link AssertionError} if any sync issue is raised during the model loading.
     */
    @NonNull
    public Map<String, AndroidProject> getAllModelsWithBenchmark(
            @Nullable String benchmarkName,
            @Nullable BenchmarkMode benchmarkMode) {
        Map<String, AndroidProject> allModels = getAllModels(
                new GetAndroidModelAction<AndroidProject>(AndroidProject.class),
                false,
                benchmarkName,
                benchmarkMode);
        for (AndroidProject project : allModels.values()) {
            assertNoSyncIssues(project);
        }
        return allModels;
    }


    private static void assertNoSyncIssues(AndroidProject project) {
        if (!project.getSyncIssues().isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Project ")
                    .append(project.getName())
                    .append(" had sync issues :\n");
            for (SyncIssue syncIssue : project.getSyncIssues()) {
                msg.append(syncIssue);
                msg.append("\n");
            }
            fail(msg.toString());
        }
    }

    /**
     * Returns a project model for each sub-project without building and ignoring potential sync
     * issues. Sync issues will still be returned for each {@link AndroidProject} that failed to
     * sync properly.
     */
    @NonNull
    public Map<String, AndroidProject> getAllModelsIgnoringSyncIssues() {
        return getAllModels(new GetAndroidModelAction<AndroidProject>(AndroidProject.class), false, null, null);
    }

    /**
     * Returns a project model for each sub-project without building.
     *
     * @param action the build action to gather the model
     */
    @NonNull
    public <K, V> Map<K, V> getAllModels(@NonNull BuildAction<Map<K, V>> action) {
        return getAllModels(action, false, null, null);
    }

    /**
     * Returns a project model for each sub-project without building.
     *
     * @param action the build action to gather the model
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param benchmarkName optional benchmark name to pass to Gradle
     * @param benchmarkMode optional benchmark mode to pass to gradle.
     */
    @NonNull
    public <K, V> Map<K, V> getAllModels(
            @NonNull BuildAction<Map<K, V>> action,
            boolean emulateStudio_1_0,
            @Nullable String benchmarkName,
            @Nullable BenchmarkMode benchmarkMode) {
        ProjectConnection connection = getProjectConnection();
        try {
            return buildModel(connection, action, emulateStudio_1_0, benchmarkName, benchmarkMode);

        } finally {
            connection.close();
        }
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param arguments List of arguments for the gradle command.
     * @param returnModel whether the model should be queried and returned.
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param expectedBuildResult the expected result. If the build status does not match the
     *                            expected failure, then an exception will be thrown.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model, if <var>returnModel</var> was true, null otherwise
     */
    @Nullable
    private <T> T execute(
            Class<T> type,
            @NonNull List<String> arguments,
            boolean returnModel,
            boolean emulateStudio_1_0,
            ExpectedBuildResult expectedBuildResult,
            @NonNull String ... tasks) {
        ProjectConnection connection = getProjectConnection();
        try {
            executeBuild(arguments, connection, tasks, expectedBuildResult);

            if (returnModel) {
                Map<String, T> modelMap = buildModel(
                        connection,
                        new GetAndroidModelAction(type),
                        emulateStudio_1_0,
                        null,
                        null);

                // ensure there was only one project
                assertEquals("Quering GradleTestProject.getModel() with multi-project settings",
                        1, modelMap.size());

                return modelMap.get(":");
            }
        } finally {
            connection.close();
        }

        return null;
    }

    private enum ExpectedBuildResult {
        SUCCESS,
        FAILURE
    }

    private void executeBuild(final List<String> arguments, ProjectConnection connection,
            final String[] tasks, ExpectedBuildResult expectedBuildResult) {
        List<String> args = Lists.newArrayListWithCapacity(5 + arguments.size());
        args.add("-i");
        args.add("-u");
        args.add("-Pcom.android.build.gradle.integratonTest.useJack=" + Boolean.toString(useJack));
        args.add("-Pcom.android.build.gradle.integratonTest.minifyEnabled=" +
                Boolean.toString(minifyEnabled));
        args.add("-Pcom.android.build.gradle.integratonTest.useComponentModel=" +
                Boolean.toString(experimentalMode));
        args.addAll(arguments);

        BuildLauncher launcher = connection.newBuild()
                .forTasks(tasks)
                .withArguments(Iterables.toArray(args, String.class));

        setJvmArguments(launcher);

        if (stdout != null) {
            launcher.setStandardOutput(stdout);
        }
        if (stderr != null) {
            launcher.setStandardError(stderr);
        }
        if (expectedBuildResult == ExpectedBuildResult.SUCCESS) {
            launcher.run();
        } else {
            launcher.run(new ResultHandler<Void>() {
                @Override
                public void onComplete(Void aVoid) {
                    throw new AssertionError(
                            String.format(
                                    "Expecting build to fail:\n" +
                                            "    Tasks:     %s\n" +
                                            "    Arguments: %s",
                                    Joiner.on(' ').join(tasks),
                                    Joiner.on(' ').join(arguments)));
                }

                @Override
                public void onFailure(GradleConnectionException e) {
                    // Ignore, the test expects this build to fail.
                }
            });
        }
    }

    private void setJvmArguments(LongRunningOperation launcher) {
        List<String> jvmArguments = new ArrayList<String>();

        if (!Strings.isNullOrEmpty(heapSize)) {
            jvmArguments.add("-Xmx" + heapSize);
        }

        jvmArguments.add("-XX:MaxPermSize=1024m");

        String debugIntegrationTest = System.getenv("DEBUG_INNER_TEST");
        if (!Strings.isNullOrEmpty(debugIntegrationTest)) {
            jvmArguments.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006");
        }

        if (JacocoAgent.isJacocoEnabled()) {
            jvmArguments.add(JacocoAgent.getJvmArg());
        }

        launcher.setJvmArguments(Iterables.toArray(jvmArguments, String.class));
    }

    /**
     * Returns a project model for each sub-project without building.
     * @param connection the opened ProjectConnection
     * @param action the build action to gather the model
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param benchmarkName optional benchmark name to pass to Gradle
     * @param benchmarkMode optional benchmark mode to pass to gradle.
     */
    @NonNull
    private <K,V> Map<K, V> buildModel(
            @NonNull ProjectConnection connection,
            @NonNull BuildAction<Map<K, V>> action,
            boolean emulateStudio_1_0,
            @Nullable String benchmarkName,
            @Nullable BenchmarkMode benchmarkMode) {

        BuildActionExecuter<Map<K, V>> executor = connection.action(action);

        List<String> arguments = Lists.newArrayListWithCapacity(5);
        arguments.add("-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY + "=true");
        arguments.add("-P" + AndroidProject.PROPERTY_INVOKED_FROM_IDE + "=true");
        if (!emulateStudio_1_0) {
            arguments.add("-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
        }
        if (benchmarkName != null) {
            arguments.add("-P" + RECORD_BENCHMARK_NAME + "=" + benchmarkName);
            if (benchmarkMode != null) {
                arguments.add("-P" + RECORD_BENCHMARK_MODE + "=" + benchmarkMode.name().toLowerCase(Locale.US));
            }
        }

        setJvmArguments(executor);

        executor.withArguments(Iterables.toArray(arguments, String.class));

        executor.setStandardOutput(System.out);
        executor.setStandardError(System.err);

        return executor.run();
    }

    /**
     * Return the stdout from all execute command.
     */
    public ByteArrayOutputStream getStdout() {
        return stdout;
    }

    /**
     * Return the stderr from all execute command.
     */
    public ByteArrayOutputStream getStderr() {
        return stderr;
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
        GradleConnector connector = GradleConnector.newConnector();

        // Limit daemon idle time for tests. 10 seconds is enough for another test
        // to start and reuse the daemon.
        ((DefaultGradleConnector) connector).daemonMaxIdleTime(10, TimeUnit.SECONDS);

        return connector
                .useGradleVersion(targetGradleVersion)
                .forProjectDirectory(testDir)
                .connect();
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


    public static void assumeLocalDevice() {
        Assume.assumeTrue(
                "Install task not run against device provider",
                GradleTestProject.REMOTE_TEST_PROVIDER == null);
    }

    public static void assumeBuildToolsAtLeast(int major) {
        assumeBuildToolsAtLeast(
                major, FullRevision.IMPLICIT_MINOR_REV, FullRevision.IMPLICIT_MICRO_REV);
    }

    public static void assumeBuildToolsAtLeast(int major, int minor, int micro) {
        FullRevision currentVersion = FullRevision.parseRevision(DEFAULT_BUILD_TOOL_VERSION);
        Assume.assumeTrue("Test is only applicable to build tools > " + major,
                new FullRevision(major, minor, micro).compareTo(currentVersion) < 0);
    }

    /**
     * Replace a line from a file with another line.
     * @param relativePath the relative path of the file from the root of the project
     * @param lineNumber the line number, starting at 1
     * @param line the line to replace with.
     * @throws IOException
     *
     * TODO: Inline.
     */
    public void replaceLine(
            String relativePath,
            int lineNumber,
            String line) throws IOException {
        FileHelper.replaceLine(file(relativePath), lineNumber, line);
    }

    public void replaceInFile(
            String relativePath,
            String search,
            String replace) throws IOException {
        File file = new File(testDir, relativePath.replace("/", File.separator));
        String content = Files.toString(file, Charset.defaultCharset());
        String newContent = content.replaceAll(search, replace);
        Files.write(newContent, file, Charset.defaultCharset());
    }
}
