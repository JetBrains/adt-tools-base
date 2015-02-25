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
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public static final int DEFAULT_COMPILE_SDK_VERSION = 21;
    public static final String DEFAULT_BUILD_TOOL_VERSION = "21.0.1";

    public static final String GRADLE_TEST_VERSION = "2.2.1";
    public static final String GRADLE_EXP_TEST_VERSION = "2.4-20150121230028+0000";

    private static final String ANDROID_GRADLE_VERSION = "1.2.0-alpha1";
    private static final String COMMON_HEADER = "commonHeader.gradle";
    private static final String COMMON_BUILD_SCRIPT = "commonBuildScript.gradle";
    private static final String COMMON_BUILD_SCRIPT_EXP = "commonBuildScriptExperimental.gradle";
    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    public static class Builder {
        private static final File SAMPLE_PROJECT_DIR = new File("samples");
        private static final File TEST_PROJECT_DIR = new File("test-projects");

        @Nullable
        private String name;

        @Nullable
        private TestProject testProject = null;

        boolean captureStdOut = false;
        boolean captureStdErr = false;
        boolean experimentalMode = false;

        /**
         * Create a GradleTestProject.
         */
        public GradleTestProject create()  {
            return new GradleTestProject(
                    name,
                    testProject,
                    experimentalMode,
                    experimentalMode ? GRADLE_EXP_TEST_VERSION : GRADLE_TEST_VERSION,
                    captureStdOut,
                    captureStdErr);
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

        public Builder forExpermimentalPlugin(boolean mode) {
            this.experimentalMode = mode;
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

        static class EmptyTestApp extends AbstractAndroidTestApp {
        }
    }

    private String name;

    private File outDir;

    private File testDir;

    private File sourceDir;

    private File buildFile;

    private File ndkDir;

    private File sdkDir;

    private final ByteArrayOutputStream stdout;
    private final ByteArrayOutputStream stderr;

    @Nullable
    private TestProject testProject;

    private boolean experimentalMode;
    private String targetGradleVersion;

    private GradleTestProject() {
        this(null, null, false, GRADLE_TEST_VERSION, false, false);
    }

    private GradleTestProject(
            @Nullable String name,
            @Nullable TestProject testProject,
            boolean experimentalMode,
            String targetGradleVersion,
            boolean captureStdOut,
            boolean captureStdErr) {
        sdkDir = SdkHelper.findSdkDir();
        ndkDir = findNdkDir();
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.experimentalMode = experimentalMode;
        this.targetGradleVersion = targetGradleVersion;
        this.testProject = testProject;
        stdout = captureStdOut ? new ByteArrayOutputStream() : null;
        stderr = captureStdErr ? new ByteArrayOutputStream() : null;
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
        testProject = null;
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

    /**
     * Add all files in a directory to an AndroidTestApp.
     */
    private static void addAllFiles(AndroidTestApp app, File projectDir) {
        for (String filePath : FileHelper.listFiles(projectDir)) {
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
    public Statement apply(final Statement base, Description description) {
        testDir = new File(outDir, description.getTestClass().getName());

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (description.getMethodName() != null) {
            testDir = new File(testDir, description.getMethodName());
        }
        testDir = new File(testDir, name);

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (testDir.exists()) {
                    deleteRecursive(testDir);
                }
                assertTrue(testDir.mkdirs());
                assertTrue(sourceDir.mkdirs());

                Files.copy(
                        new File(Builder.TEST_PROJECT_DIR, COMMON_HEADER),
                        new File(testDir.getParent(), COMMON_HEADER));
                Files.copy(
                        new File(Builder.TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT),
                        new File(testDir.getParent(), COMMON_BUILD_SCRIPT));
                Files.copy(
                        new File(Builder.TEST_PROJECT_DIR, COMMON_BUILD_SCRIPT_EXP),
                        new File(testDir.getParent(), COMMON_BUILD_SCRIPT_EXP));

                if (testProject != null) {
                    testProject.write(testDir, getGradleBuildscript());
                } else {
                    Files.write(
                            getGradleBuildscript(),
                            buildFile,
                            Charsets.UTF_8);
                }

                createLocalProp(testDir, sdkDir, ndkDir);
                base.evaluate();
            }
        };
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
        return "buildscript {\n" +
                "    repositories {\n" +
                "        maven { url '" + getRepoDir().toString() + "' }\n" +
                "    }\n" +
                "    dependencies {\n" +
                "        classpath \"com.android.tools.build:gradle" + (experimentalMode ? "-experimental" : "") + ":" + ANDROID_GRADLE_VERSION + "\"\n" +
                "    }\n" +
                "}\n";
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
        execute(Collections.<String>emptyList(), false, false, tasks);
    }

    public void execute(@NonNull List<String> arguments, String ... tasks) {
        execute(arguments, false, false, tasks);
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
        return execute(Collections.<String>emptyList(), true, emulateStudio_1_0, tasks);
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
            executeBuild(Collections.<String>emptyList(), connection, tasks);

            return buildModel(connection, new GetAndroidModelAction(), emulateStudio_1_0);

        } finally {
            connection.close();
        }
    }

    /**
     * Returns the project model without building.
     *
     * This will fail if the project is a multi-project setup.
     */
    @NonNull
    public AndroidProject getSingleModel() {
        return getSingleModel(false);
    }

    /**
     * Returns the project model without building.
     *
     * This will fail if the project is a multi-project setup.
     *
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     */
    @NonNull
    public AndroidProject getSingleModel(boolean emulateStudio_1_0) {
        ProjectConnection connection = getProjectConnection();
        try {
            Map<String, AndroidProject> modelMap = buildModel(
                    connection,
                    new GetAndroidModelAction(),
                    emulateStudio_1_0);

            // ensure there was only one project
            assertEquals("Quering GradleTestProject.getModel() with multi-project settings",
                    1, modelMap.size());

            return modelMap.get(":");
        } finally {
            connection.close();
        }
    }

    /**
     * Returns a project model for each sub-project without building.
     */
    @NonNull
    public Map<String, AndroidProject> getAllModels() {
        return getAllModels(new GetAndroidModelAction(), false);
    }

    /**
     * Returns a project model for each sub-project without building.
     *
     * @param action the build action to gather the model
     */
    @NonNull
    public <K, V> Map<K, V> getAllModels(@NonNull BuildAction<Map<K, V>> action) {
        return getAllModels(action, false);
    }

    /**
     * Returns a project model for each sub-project without building.
     *
     * @param action the build action to gather the model
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     */
    @NonNull
    public <K, V> Map<K, V> getAllModels(
            @NonNull BuildAction<Map<K, V>> action,
            boolean emulateStudio_1_0) {
        ProjectConnection connection = getProjectConnection();
        try {
            return buildModel(connection, action, emulateStudio_1_0);

        } finally {
            connection.close();
        }
    }

    public void evaluate() {
        getAllModels(new GetEmptyModelAction(), false);
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param arguments List of arguments for the gradle command.
     * @param returnModel whether the model should be queried and returned.
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model, if <var>returnModel</var> was true, null otherwise
     */
    @Nullable
    private AndroidProject execute(
            @NonNull List<String> arguments,
            boolean returnModel,
            boolean emulateStudio_1_0,
            @NonNull String ... tasks) {
        ProjectConnection connection = getProjectConnection();
        try {
            executeBuild(arguments, connection, tasks);

            if (returnModel) {
                Map<String, AndroidProject> modelMap = buildModel(
                        connection,
                        new GetAndroidModelAction(),
                        emulateStudio_1_0);

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

    private void executeBuild(List<String> arguments, ProjectConnection connection,
            String[] tasks) {
        List<String> args = Lists.newArrayListWithCapacity(2 + arguments.size());
        args.add("-i");
        args.add("-u");
        args.addAll(arguments);

        BuildLauncher launcher = connection.newBuild().forTasks(tasks)
                .withArguments(args.toArray(new String[args.size()]));

        List<String> jvmArguments = getDebugJvmArguments();

        if (JacocoAgent.isJacocoEnabled()) {
            jvmArguments.add(JacocoAgent.getJvmArg());
        }
        if (!jvmArguments.isEmpty()) {
            launcher.setJvmArguments(jvmArguments.toArray(new String[jvmArguments.size()]));
        }

        if (stdout != null) {
            launcher.setStandardOutput(stdout);
        }
        if (stderr != null) {
            launcher.setStandardError(stderr);
        }
        launcher.run();
    }

    private static List<String> getDebugJvmArguments() {
        List<String> jvmArguments = new ArrayList<String>();
        String debugIntegrationTest = System.getenv("DEBUG_INNER_TEST");
        if (!Strings.isNullOrEmpty(debugIntegrationTest)) {
            jvmArguments.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        return jvmArguments;
    }

    /**
     * Returns a project model for each sub-project without building.
     *
     * @param connection the opened ProjectConnection
     * @param action the build action to gather the model
     * @param emulateStudio_1_0 whether to emulate an older IDE (studio 1.0) querying the model.
     */
    @NonNull
    private static <K,V> Map<K, V> buildModel(
            @NonNull ProjectConnection connection,
            @NonNull BuildAction<Map<K, V>> action,
            boolean emulateStudio_1_0) {

        BuildActionExecuter<Map<K, V>> executer = connection.action(action);

        executer.withArguments(
                "-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY + "=true",
                "-P" + AndroidProject.PROPERTY_INVOKED_FROM_IDE + "=true");
        if (!emulateStudio_1_0) {
            executer.withArguments(
                    "-P" + AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED + "=true");
        }

        executer.setJvmArguments(Iterables.toArray(getDebugJvmArguments(), String.class));
        return executer.run();
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
        File result = new File(path);
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
}
