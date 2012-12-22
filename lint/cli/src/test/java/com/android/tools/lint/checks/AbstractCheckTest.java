/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.SdkTestCase;
import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LombokParser;
import com.android.tools.lint.Main;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.TextReporter;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Common utility methods for the various lint check tests */
@SuppressWarnings("javadoc")
public abstract class AbstractCheckTest extends SdkTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        BuiltinIssueRegistry.reset();
    }

    protected abstract Detector getDetector();

    private Detector mDetector;

    private Detector getDetectorInstance() {
        if (mDetector == null) {
            mDetector = getDetector();
        }

        return mDetector;
    }

    protected List<Issue> getIssues() {
        List<Issue> issues = new ArrayList<Issue>();
        Class<? extends Detector> detectorClass = getDetectorInstance().getClass();
        // Get the list of issues from the registry and filter out others, to make sure
        // issues are properly registered
        List<Issue> candidates = new BuiltinIssueRegistry().getIssues();
        for (Issue issue : candidates) {
            if (issue.getDetectorClass() == detectorClass) {
                issues.add(issue);
            }
        }

        return issues;
    }

    private class CustomIssueRegistry extends IssueRegistry {
        @Override
        public List<Issue> getIssues() {
            return AbstractCheckTest.this.getIssues();
        }
    }

    protected String lintFiles(String... relativePaths) throws Exception {
        List<File> files = new ArrayList<File>();
        File targetDir = getTargetDir();
        for (String relativePath : relativePaths) {
            File file = getTestfile(targetDir, relativePath);
            assertNotNull(file);
            files.add(file);
        }

        addManifestFile(targetDir);

        return checkLint(files);
    }

    protected String checkLint(List<File> files) throws Exception {
        mOutput = new StringBuilder();
        TestLintClient lintClient = createClient();
        String result = lintClient.analyze(files);

        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }

        for (File f : files) {
            deleteFile(f);
        }

        return result;
    }

    protected TestLintClient createClient() {
        return new TestLintClient();
    }

    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null);
    }

    protected void configureDriver(LintDriver driver) {
    }

    /**
     * Run lint on the given files when constructed as a separate project
     * @return The output of the lint check. On Windows, this transforms all directory
     *   separators to the unix-style forward slash.
     */
    protected String lintProject(String... relativePaths) throws Exception {
        File projectDir = getProjectDir(null, relativePaths);
        return checkLint(Collections.singletonList(projectDir));
    }

    @Override
    protected File getTargetDir() {
        File targetDir = new File(getTempDir(), getClass().getSimpleName() + "_" + getName());
        addCleanupDir(targetDir);
        return targetDir;
    }

    /** Creates a project directory structure from the given files */
    protected File getProjectDir(String name, String ...relativePaths) throws Exception {
        assertFalse("getTargetDir must be overridden to make a unique directory",
                getTargetDir().equals(getTempDir()));

        File projectDir = getTargetDir();
        if (name != null) {
            projectDir = new File(projectDir, name);
        }
        if (!projectDir.exists()) {
            assertTrue(projectDir.getPath(), projectDir.mkdirs());
        }

        List<File> files = new ArrayList<File>();
        for (String relativePath : relativePaths) {
            File file = getTestfile(projectDir, relativePath);
            assertNotNull(file);
            files.add(file);
        }

        addManifestFile(projectDir);
        return projectDir;
    }

    private void addManifestFile(File projectDir) throws IOException {
        // Ensure that there is at least a manifest file there to make it a valid project
        // as far as Lint is concerned:
        if (!new File(projectDir, "AndroidManifest.xml").exists()) {
            File manifest = new File(projectDir, "AndroidManifest.xml");
            FileWriter fw = new FileWriter(manifest);
            fw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"foo.bar2\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "</manifest>\n");
            fw.close();
        }
    }

    private StringBuilder mOutput = null;

    @Override
    protected InputStream getTestResource(String relativePath, boolean expectExists) {
        String path = "data" + File.separator + relativePath; //$NON-NLS-1$
        InputStream stream =
            AbstractCheckTest.class.getResourceAsStream(path);
        if (stream == null) {
            File root = getRootDir();
            assertNotNull(root);
            String pkg = AbstractCheckTest.class.getName();
            pkg = pkg.substring(0, pkg.lastIndexOf('.'));
            File f = new File(root,
                    "tools/base/lint/cli/src/test/java/".replace('/', File.separatorChar)
                        + pkg.replace('.', File.separatorChar)
                        + File.separatorChar + path);
            if (f.exists()) {
                try {
                    return new BufferedInputStream(new FileInputStream(f));
                } catch (FileNotFoundException e) {
                    stream = null;
                    if (expectExists) {
                        fail("Could not find file " + relativePath);
                    }
                }
            }
        }
        if (!expectExists && stream == null) {
            return null;
        }
        return stream;
    }

    protected boolean isEnabled(Issue issue) {
        Class<? extends Detector> detectorClass = getDetectorInstance().getClass();
        if (issue.getDetectorClass() == detectorClass) {
            return true;
        }

        if (issue == IssueRegistry.LINT_ERROR || issue == IssueRegistry.PARSER_ERROR) {
            return !ignoreSystemErrors();
        }

        return false;
    }

    protected boolean includeParentPath() {
        return false;
    }

    protected EnumSet<Scope> getLintScope(List<File> file) {
        return null;
    }

    public String getSuperClass(Project project, String name) {
        return null;
    }

    protected boolean ignoreSystemErrors() {
        return true;
    }

    public class TestLintClient extends Main {
        private StringWriter mWriter = new StringWriter();

        public TestLintClient() {
            mReporters.add(new TextReporter(this, mWriter, false));
        }

        @Override
        public String getSuperClass(Project project, String name) {
            String superClass = AbstractCheckTest.this.getSuperClass(project, name);
            if (superClass != null) {
                return superClass;
            }

            return super.getSuperClass(project, name);
        }

        public String analyze(List<File> files) throws Exception {
            mDriver = new LintDriver(new CustomIssueRegistry(), this);
            configureDriver(mDriver);
            mDriver.analyze(files, getLintScope(files));

            Collections.sort(mWarnings);

            for (Reporter reporter : mReporters) {
                reporter.write(mErrorCount, mWarningCount, mWarnings);
            }

            mOutput.append(mWriter.toString());

            if (mOutput.length() == 0) {
                mOutput.append("No warnings.");
            }

            String result = mOutput.toString();
            if (result.equals("\nNo issues found.\n")) {
                result = "No warnings.";
            }

            result = cleanup(result);

            return result;
        }

        public String getErrors() throws Exception {
            return mWriter.toString();
        }

        @Override
        public void report(
                @NonNull Context context,
                @NonNull Issue issue,
                @NonNull Severity severity,
                @Nullable Location location,
                @NonNull String message,
                @Nullable Object data) {
            if (ignoreSystemErrors() && (issue == IssueRegistry.LINT_ERROR)) {
                return;
            }

            if (severity == Severity.FATAL) {
                // Treat fatal errors like errors in the golden files.
                severity = Severity.ERROR;
            }

            // For messages into all secondary locations to ensure they get
            // specifically included in the text report
            if (location != null && location.getSecondary() != null) {
                Location l = location.getSecondary();
                while (l != null) {
                    if (l.getMessage() == null) {
                        l.setMessage("<No location-specific message");
                    }
                    l = l.getSecondary();
                }
            }

            super.report(context, issue, severity, location, message, data);
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            if (exception != null) {
                exception.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            if (format != null) {
                sb.append(String.format(format, args));
            }
            if (exception != null) {
                sb.append(exception.toString());
            }
            System.err.println(sb);

            if (exception != null) {
                fail(exception.toString());
            }
        }

        @Override
        public IDomParser getDomParser() {
            return new LintCliXmlParser();
        }

        @Override
        public IJavaParser getJavaParser() {
            return new LombokParser();
        }

        @Override
        public Configuration getConfiguration(@NonNull Project project) {
            return AbstractCheckTest.this.getConfiguration(this, project);
        }

        @Override
        public File findResource(String relativePath) {
            if (relativePath.equals("platform-tools/api/api-versions.xml")) {
                File rootDir = getRootDir();
                if (rootDir != null) {
                    File file = new File(rootDir, "development" + File.separator + "sdk"
                            + File.separator + "api-versions.xml");
                    return file;
                }
            } else if (relativePath.startsWith("tools/support/")) {
                String base = relativePath.substring("tools/support/".length());
                File rootDir = getRootDir();
                if (rootDir != null) {
                    File file = new File(rootDir, "tools"
                            + File.separator + "base"
                            + File.separator + "files"
                            + File.separator + "typos"
                            + File.separator + base);
                    return file;
                }
            } else {
                fail("Unit tests don't support arbitrary resource lookup yet.");
            }

            return super.findResource(relativePath);
        }
    }

    /**
     * Returns the Android source tree root dir.
     * @return the root dir or null if it couldn't be computed.
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());
                while (dir != null) {
                    File settingsGradle = new File(dir, "settings.gradle"); //$NON-NLS-1$
                    if (settingsGradle.exists()) {
                        return dir.getParentFile().getParentFile();
                    }
                    File lint = new File(dir, "lint");  //$NON-NLS-1$
                    if (lint.exists() && new File(lint, "cli").exists()) { //$NON-NLS-1$
                        return dir.getParentFile().getParentFile();
                    }
                    dir = dir.getParentFile();
                }

                return null;
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        return null;
    }

    public class TestConfiguration extends DefaultConfiguration {
        protected TestConfiguration(
                @NonNull LintClient client,
                @NonNull Project project,
                @Nullable Configuration parent) {
            super(client, project, parent);
        }

        public TestConfiguration(
                @NonNull LintClient client,
                @Nullable Project project,
                @Nullable Configuration parent,
                @NonNull File configFile) {
            super(client, project, parent, configFile);
        }

        @Override
        @NonNull
        protected Severity getDefaultSeverity(@NonNull Issue issue) {
            // In unit tests, include issues that are ignored by default
            Severity severity = super.getDefaultSeverity(issue);
            if (severity == Severity.IGNORE) {
                return Severity.WARNING;
            }
            return severity;
        }

        @Override
        public boolean isEnabled(@NonNull Issue issue) {
            return AbstractCheckTest.this.isEnabled(issue);
        }

        @Override
        public void ignore(@NonNull Context context, @NonNull Issue issue,
                @Nullable Location location, @NonNull String message, @Nullable Object data) {
            fail("Not supported in tests.");
        }

        @Override
        public void setSeverity(@NonNull Issue issue, @Nullable Severity severity) {
            fail("Not supported in tests.");
        }
    }
}
