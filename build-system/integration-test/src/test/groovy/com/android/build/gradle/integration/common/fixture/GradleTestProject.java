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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    public static final int DEFAULT_COMPILE_SDK_VERSION = 21;

    public static final String DEFAULT_BUILD_TOOL_VERSION = "21.0.1";

    private static final String ANDROID_GRADLE_VERSION = "1.0.0-rc4";

    public static class Builder {
        private static final File SAMPLE_PROJECT_DIR = new File("samples");

        private static final File TEST_PROJECT_DIR = new File("test-projects");

        @Nullable
        private String name;

        @Nullable
        private AndroidTestApp testApp = null;

        boolean captureStdOut = false;

        /**
         * Create a GradleTestProject.
         */
        public GradleTestProject create()  {
            return new GradleTestProject(name, testApp, captureStdOut);
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

        /**
         * Create GradleTestProject from an AndroidTestApp.
         */
        public Builder fromTestApp(@NonNull AndroidTestApp testApp) {
            this.testApp = testApp;
            return this;
        }

        /**
         * Create GradleTestProjectBase from an existing sample project.
         */
        public Builder fromSample(@NonNull String project) {
            // Create a new AndroidTestApp with all files in the project.
            AndroidTestApp app = new EmptyTestApp();
            File projectDir = new File(SAMPLE_PROJECT_DIR, project);
            addAllFiles(app, projectDir);
            return fromTestApp(app);
        }

        /**
         * Create GradleTestProject from an existing test project.
         */
        public Builder fromTestProject(@NonNull String project) {
            AndroidTestApp app = new EmptyTestApp();
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

    private ByteArrayOutputStream stdout;

    @Nullable
    private AndroidTestApp testApp;

    private GradleTestProject() {
        this(null, null, false);
    }

    private GradleTestProject(
            @Nullable String name,
            @Nullable AndroidTestApp testApp,
            boolean captureStdOut) {
        sdkDir = SdkHelper.findSdkDir();
        ndkDir = findNdkDir();
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.testApp = testApp;
        if (captureStdOut) {
            stdout = new ByteArrayOutputStream();
        }
    }

    /**
     * Create a GradleTestProject representing a subproject of another GradleTestProject.
     * @param subproject name of the subproject.
     * @param rootProject root GradleTestProject.
     */
    private GradleTestProject(
            @NonNull String subproject,
            @NonNull GradleTestProject rootProject) {
        name = subproject;
        outDir = rootProject.outDir;

        testDir = new File(rootProject.testDir, subproject);
        assertTrue(testDir.isDirectory());

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");
        ndkDir = rootProject.ndkDir;
        sdkDir = rootProject.sdkDir;
        stdout = rootProject.stdout;
        testApp = null;
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
        for (File src : Files.fileTreeTraverser().preOrderTraversal(projectDir).filter(
                new Predicate<File>() {
                    @Override
                    public boolean apply(@Nullable File file) {
                        return file != null && !file.isDirectory();
                    }
                })) {
            File relativePath = new File(src.toString().replace(projectDir.toString(), ""));
            try {
                app.addFile(
                        new TestSourceFile(
                                relativePath.getParent(),
                                src.getName(),
                                Files.toByteArray(src)));
            } catch (Exception e) {
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

                if (testApp != null) {
                    testApp.writeSources(testDir);
                } else {
                    Files.write(
                            "buildscript {\n" +
                                    "    repositories {\n" +
                                    "        maven { url '" + getRepoDir().toString() + "' }\n" +
                                    "    }\n" +
                                    "    dependencies {\n" +
                                    "        classpath \"com.android.tools.build:gradle:" + ANDROID_GRADLE_VERSION + "\"\n" +
                                    "    }\n" +
                                    "}\n",
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
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(String ... tasks) {
        execute(Collections.<String>emptyList(), false, tasks);
    }

    public void execute(@NonNull List<String> arguments, String ... tasks) {
        execute(arguments, false, tasks);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    public AndroidProject executeAndReturnModel(String ... tasks) {
        return execute(Collections.<String>emptyList(), true, tasks);
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param arguments List of arguments for the gradle command.
     * @param returnModel whether the model should be queried and returned.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model, if <var>returnModel</var> was true, null otherwise
     */
    @Nullable
    private AndroidProject execute(
            @NonNull List<String> arguments,
            boolean returnModel,
            @NonNull String ... tasks) {
        ProjectConnection connection = getProjectConnection();
        try {
            List<String> args = Lists.newArrayListWithCapacity(2 + arguments.size());
            args.add("-i");
            args.add("-u");
            args.addAll(arguments);

            BuildLauncher launcher = connection.newBuild().forTasks(tasks)
                    .withArguments(args.toArray(new String[args.size()]));
            if (stdout != null) {
                launcher.setStandardOutput(stdout);
            }
            launcher.run();

            if (returnModel) {
                return connection.getModel(AndroidProject.class);
            }
        } finally {
            connection.close();
        }

        return null;
    }

    /**
     * Returns the project model
     */
    @NonNull
    public AndroidProject getModel() {
        ProjectConnection connection = getProjectConnection();
        try {
            return connection.getModel(AndroidProject.class);
        } finally {
            connection.close();
        }
    }

    /**
     * Return the stdout from all execute command.
     */
    public ByteArrayOutputStream getStdout() {
        return stdout;
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
                .useGradleVersion(BasePlugin.GRADLE_TEST_VERSION)
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
