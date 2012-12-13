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

package com.android.tools.lint;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.lint.client.api.IssueRegistry.LINT_ERROR;
import static com.android.tools.lint.client.api.IssueRegistry.PARSER_ERROR;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Command line driver for the lint framework
 */
public class Main extends LintClient {
    static final int MAX_LINE_WIDTH = 78;
    private static final String ARG_ENABLE     = "--enable";       //$NON-NLS-1$
    private static final String ARG_DISABLE    = "--disable";      //$NON-NLS-1$
    private static final String ARG_CHECK      = "--check";        //$NON-NLS-1$
    private static final String ARG_IGNORE     = "--ignore";       //$NON-NLS-1$
    private static final String ARG_LISTIDS    = "--list";         //$NON-NLS-1$
    private static final String ARG_SHOW       = "--show";         //$NON-NLS-1$
    private static final String ARG_QUIET      = "--quiet";        //$NON-NLS-1$
    private static final String ARG_FULLPATH   = "--fullpath";     //$NON-NLS-1$
    private static final String ARG_SHOWALL    = "--showall";      //$NON-NLS-1$
    private static final String ARG_HELP       = "--help";         //$NON-NLS-1$
    private static final String ARG_NOLINES    = "--nolines";      //$NON-NLS-1$
    private static final String ARG_HTML       = "--html";         //$NON-NLS-1$
    private static final String ARG_SIMPLEHTML = "--simplehtml";   //$NON-NLS-1$
    private static final String ARG_XML        = "--xml";          //$NON-NLS-1$
    private static final String ARG_TEXT       = "--text";         //$NON-NLS-1$
    private static final String ARG_CONFIG     = "--config";       //$NON-NLS-1$
    private static final String ARG_URL        = "--url";          //$NON-NLS-1$
    private static final String ARG_VERSION    = "--version";      //$NON-NLS-1$
    private static final String ARG_EXITCODE   = "--exitcode";     //$NON-NLS-1$
    private static final String ARG_CLASSES    = "--classpath";    //$NON-NLS-1$
    private static final String ARG_SOURCES    = "--sources";      //$NON-NLS-1$
    private static final String ARG_LIBRARIES  = "--libraries";    //$NON-NLS-1$

    private static final String ARG_NOWARN2    = "--nowarn";       //$NON-NLS-1$
    // GCC style flag names for options
    private static final String ARG_NOWARN1    = "-w";             //$NON-NLS-1$
    private static final String ARG_WARNALL    = "-Wall";          //$NON-NLS-1$
    private static final String ARG_ALLERROR   = "-Werror";        //$NON-NLS-1$

    private static final String VALUE_NONE     = "none";           //$NON-NLS-1$

    private static final String PROP_WORK_DIR = "com.android.tools.lint.workdir"; //$NON-NLS-1$

    private static final int ERRNO_ERRORS = 1;
    private static final int ERRNO_USAGE = 2;
    private static final int ERRNO_EXISTS = 3;
    private static final int ERRNO_HELP = 4;
    private static final int ERRNO_INVALIDARGS = 5;

    protected final List<Warning> mWarnings = new ArrayList<Warning>();
    protected final Set<String> mSuppress = new HashSet<String>();
    protected final Set<String> mEnabled = new HashSet<String>();
    /** If non-null, only run the specified checks (possibly modified by enable/disables) */
    protected Set<String> mCheck = null;
    protected boolean mHasErrors;
    protected boolean mSetExitCode;
    protected boolean mFullPath;
    protected int mErrorCount;
    protected int mWarningCount;
    protected boolean mShowLines = true;
    protected final List<Reporter> mReporters = Lists.newArrayList();
    protected boolean mQuiet;
    protected boolean mWarnAll;
    protected boolean mNoWarnings;
    protected boolean mAllErrors;
    protected List<File> mSources;
    protected List<File> mClasses;
    protected List<File> mLibraries;

    protected Configuration mDefaultConfiguration;
    protected IssueRegistry mRegistry;
    protected LintDriver mDriver;
    protected boolean mShowAll;

    /** Creates a CLI driver */
    public Main() {
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    public static void main(String[] args) {
        new Main().run(args);
    }

    /**
     * Runs the static analysis command line driver
     *
     * @param args program arguments
     */
    private void run(String[] args) {
        if (args.length < 1) {
            printUsage(System.err);
            System.exit(ERRNO_USAGE);
        }

        IssueRegistry registry = mRegistry = new BuiltinIssueRegistry();

        // Mapping from file path prefix to URL. Applies only to HTML reports
        String urlMap = null;

        List<File> files = new ArrayList<File>();
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];

            if (arg.equals(ARG_HELP)
                    || arg.equals("-h") || arg.equals("-?")) { //$NON-NLS-1$ //$NON-NLS-2$
                if (index < args.length - 1) {
                    String topic = args[index + 1];
                    if (topic.equals("suppress") || topic.equals("ignore")) {
                        printHelpTopicSuppress();
                        System.exit(ERRNO_HELP);
                    } else {
                        System.err.println(String.format("Unknown help topic \"%1$s\"", topic));
                        System.exit(ERRNO_INVALIDARGS);
                    }
                }
                printUsage(System.out);
                System.exit(ERRNO_HELP);
            } else if (arg.equals(ARG_LISTIDS)) {
                // Did the user provide a category list?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) { //$NON-NLS-1$
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // List all issues with the given category
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category) ||
                                        issue.getCategory().getFullName().startsWith(category)) {
                                    listIssue(System.out, issue);
                                }
                            }
                        } else {
                            System.err.println("Invalid category \"" + id + "\".\n");
                            displayValidIds(registry, System.err);
                            System.exit(ERRNO_INVALIDARGS);
                        }
                    }
                } else {
                    displayValidIds(registry, System.out);
                }
                System.exit(0);
            } else if (arg.equals(ARG_SHOW)) {
                // Show specific issues?
                if (index < args.length - 1 && !args[index + 1].startsWith("-")) { //$NON-NLS-1$
                    String[] ids = args[++index].split(",");
                    for (String id : ids) {
                        if (registry.isCategoryName(id)) {
                            // Show all issues in the given category
                            String category = id;
                            for (Issue issue : registry.getIssues()) {
                                // Check prefix such that filtering on the "Usability" category
                                // will match issue category "Usability:Icons" etc.
                                if (issue.getCategory().getName().startsWith(category) ||
                                        issue.getCategory().getFullName().startsWith(category)) {
                                    describeIssue(issue);
                                    System.out.println();
                                }
                            }
                        } else if (registry.isIssueId(id)) {
                            describeIssue(registry.getIssue(id));
                            System.out.println();
                        } else {
                            System.err.println("Invalid id or category \"" + id + "\".\n");
                            displayValidIds(registry, System.err);
                            System.exit(ERRNO_INVALIDARGS);
                        }
                    }
                } else {
                    showIssues(registry);
                }
                System.exit(0);
            } else if (arg.equals(ARG_FULLPATH)
                    || arg.equals(ARG_FULLPATH + "s")) { // allow "--fullpaths" too
                mFullPath = true;
            } else if (arg.equals(ARG_SHOWALL)) {
                mShowAll = true;
            } else if (arg.equals(ARG_QUIET) || arg.equals("-q")) {
                mQuiet = true;
            } else if (arg.equals(ARG_NOLINES)) {
                mShowLines = false;
            } else if (arg.equals(ARG_EXITCODE)) {
                mSetExitCode = true;
            } else if (arg.equals(ARG_VERSION)) {
                printVersion();
                System.exit(0);
            } else if (arg.equals(ARG_URL)) {
                if (index == args.length - 1) {
                    System.err.println("Missing URL mapping string");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String map = args[++index];
                // Allow repeated usage of the argument instead of just comma list
                if (urlMap != null) {
                    urlMap = urlMap + ',' + map;
                } else {
                    urlMap = map;
                }
            } else if (arg.equals(ARG_CONFIG)) {
                if (index == args.length - 1 || !endsWith(args[index + 1], DOT_XML)) {
                    System.err.println("Missing XML configuration file argument");
                    System.exit(ERRNO_INVALIDARGS);
                }
                File file = getInArgumentPath(args[++index]);
                if (!file.exists()) {
                    System.err.println(file.getAbsolutePath() + " does not exist");
                    System.exit(ERRNO_INVALIDARGS);
                }
                mDefaultConfiguration = new CliConfiguration(file);
            } else if (arg.equals(ARG_HTML) || arg.equals(ARG_SIMPLEHTML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing HTML output file name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                File output = getOutArgumentPath(args[++index]);
                // Get an absolute path such that we can ask its parent directory for
                // write permission etc.
                output = output.getAbsoluteFile();
                if (output.isDirectory() ||
                        (!output.exists() && output.getName().indexOf('.') == -1)) {
                    if (!output.exists()) {
                        boolean mkdirs = output.mkdirs();
                        if (!mkdirs) {
                            log(null, "Could not create output directory %1$s", output);
                            System.exit(ERRNO_EXISTS);
                        }
                    }
                    try {
                        MultiProjectHtmlReporter reporter =
                                new MultiProjectHtmlReporter(this, output);
                        if (arg.equals(ARG_SIMPLEHTML)) {
                            reporter.setSimpleFormat(true);
                        }
                        mReporters.add(reporter);
                    } catch (IOException e) {
                        log(e, null);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    continue;
                }
                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        System.exit(ERRNO_EXISTS);
                    }
                }
                if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
                    System.err.println("Cannot write HTML output file " + output);
                    System.exit(ERRNO_EXISTS);
                }
                try {
                    HtmlReporter htmlReporter = new HtmlReporter(this, output);
                    if (arg.equals(ARG_SIMPLEHTML)) {
                        htmlReporter.setSimpleFormat(true);
                    }
                    mReporters.add(htmlReporter);
                } catch (IOException e) {
                    log(e, null);
                    System.exit(ERRNO_INVALIDARGS);
                }
            } else if (arg.equals(ARG_XML)) {
                if (index == args.length - 1) {
                    System.err.println("Missing XML output file name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                File output = getOutArgumentPath(args[++index]);
                if (output.exists()) {
                    boolean delete = output.delete();
                    if (!delete) {
                        System.err.println("Could not delete old " + output);
                        System.exit(ERRNO_EXISTS);
                    }
                }
                if (output.canWrite()) {
                    System.err.println("Cannot write XML output file " + output);
                    System.exit(ERRNO_EXISTS);
                }
                try {
                    mReporters.add(new XmlReporter(this, output));
                } catch (IOException e) {
                    log(e, null);
                    System.exit(ERRNO_INVALIDARGS);
                }
            } else if (arg.equals(ARG_TEXT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing XML output file name");
                    System.exit(ERRNO_INVALIDARGS);
                }

                Writer writer = null;
                boolean closeWriter;
                String outputName = args[++index];
                if (outputName.equals("stdout")) { //$NON-NLS-1$
                    writer = new PrintWriter(System.out, true);
                    closeWriter = false;
                } else {
                    File output = getOutArgumentPath(outputName);
                    if (output.exists()) {
                        boolean delete = output.delete();
                        if (!delete) {
                            System.err.println("Could not delete old " + output);
                            System.exit(ERRNO_EXISTS);
                        }
                    }
                    if (output.canWrite()) {
                        System.err.println("Cannot write XML output file " + output);
                        System.exit(ERRNO_EXISTS);
                    }
                    try {
                        writer = new BufferedWriter(new FileWriter(output));
                    } catch (IOException e) {
                        log(e, null);
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    closeWriter = true;
                }
                mReporters.add(new TextReporter(this, writer, closeWriter));
            } else if (arg.equals(ARG_DISABLE) || arg.equals(ARG_IGNORE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to disable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Suppress all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mSuppress.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mSuppress.add(id);
                    }
                }
            } else if (arg.equals(ARG_ENABLE)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to enable");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Enable all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mEnabled.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mEnabled.add(id);
                    }
                }
            } else if (arg.equals(ARG_CHECK)) {
                if (index == args.length - 1) {
                    System.err.println("Missing categories or id's to check");
                    System.exit(ERRNO_INVALIDARGS);
                }
                mCheck = new HashSet<String>();
                String[] ids = args[++index].split(",");
                for (String id : ids) {
                    if (registry.isCategoryName(id)) {
                        // Check all issues with the given category
                        String category = id;
                        for (Issue issue : registry.getIssues()) {
                            // Check prefix such that filtering on the "Usability" category
                            // will match issue category "Usability:Icons" etc.
                            if (issue.getCategory().getName().startsWith(category) ||
                                    issue.getCategory().getFullName().startsWith(category)) {
                                mCheck.add(issue.getId());
                            }
                        }
                    } else if (!registry.isIssueId(id)) {
                        System.err.println("Invalid id or category \"" + id + "\".\n");
                        displayValidIds(registry, System.err);
                        System.exit(ERRNO_INVALIDARGS);
                    } else {
                        mCheck.add(id);
                    }
                }
            } else if (arg.equals(ARG_NOWARN1) || arg.equals(ARG_NOWARN2)) {
                mNoWarnings = true;
            } else if (arg.equals(ARG_WARNALL)) {
                mWarnAll = true;
            } else if (arg.equals(ARG_ALLERROR)) {
                mAllErrors = true;
            } else if (arg.equals(ARG_CLASSES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing class folder name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String paths = args[++index];
                for (String path : LintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Class path entry " + input + " does not exist.");
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    if (mClasses == null) {
                        mClasses = new ArrayList<File>();
                    }
                    mClasses.add(input);
                }
            } else if (arg.equals(ARG_SOURCES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing source folder name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String paths = args[++index];
                for (String path : LintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Source folder " + input + " does not exist.");
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    if (mSources == null) {
                        mSources = new ArrayList<File>();
                    }
                    mSources.add(input);
                }
            } else if (arg.equals(ARG_LIBRARIES)) {
                if (index == args.length - 1) {
                    System.err.println("Missing library folder name");
                    System.exit(ERRNO_INVALIDARGS);
                }
                String paths = args[++index];
                for (String path : LintUtils.splitPath(paths)) {
                    File input = getInArgumentPath(path);
                    if (!input.exists()) {
                        System.err.println("Library " + input + " does not exist.");
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    if (mLibraries == null) {
                        mLibraries = new ArrayList<File>();
                    }
                    mLibraries.add(input);
                }
            } else if (arg.startsWith("--")) {
                System.err.println("Invalid argument " + arg + "\n");
                printUsage(System.err);
                System.exit(ERRNO_INVALIDARGS);
            } else {
                String filename = arg;
                File file = getInArgumentPath(filename);

                if (!file.exists()) {
                    System.err.println(String.format("%1$s does not exist.", filename));
                    System.exit(ERRNO_EXISTS);
                }
                files.add(file);
            }
        }

        if (files.isEmpty()) {
            System.err.println("No files to analyze.");
            System.exit(ERRNO_INVALIDARGS);
        } else if (files.size() > 1
                && (mClasses != null || mSources != null || mLibraries != null)) {
            System.err.println("The " + ARG_SOURCES + ", " + ARG_CLASSES + " and "
                    + ARG_LIBRARIES + " arguments can only be used with a single project");
            System.exit(ERRNO_INVALIDARGS);
        }

        if (mReporters.isEmpty()) {
            if (urlMap != null) {
                System.err.println(String.format(
                        "Warning: The %1$s option only applies to HTML reports (%2$s)",
                            ARG_URL, ARG_HTML));
            }

            mReporters.add(new TextReporter(this, new PrintWriter(System.out, true), false));
        } else {
            if (urlMap == null) {
                // By default just map from /foo to file:///foo
                // TODO: Find out if we need file:// on Windows.
                urlMap = "=file://"; //$NON-NLS-1$
            } else {
                for (Reporter reporter : mReporters) {
                    if (!reporter.isSimpleFormat()) {
                        reporter.setBundleResources(true);
                    }
                }
            }

            if (!urlMap.equals(VALUE_NONE)) {
                Map<String, String> map = new HashMap<String, String>();
                String[] replace = urlMap.split(","); //$NON-NLS-1$
                for (String s : replace) {
                    // Allow ='s in the suffix part
                    int index = s.indexOf('=');
                    if (index == -1) {
                        System.err.println(
                            "The URL map argument must be of the form 'path_prefix=url_prefix'");
                        System.exit(ERRNO_INVALIDARGS);
                    }
                    String key = s.substring(0, index);
                    String value = s.substring(index + 1);
                    map.put(key, value);
                }
                for (Reporter reporter : mReporters) {
                    reporter.setUrlMap(map);
                }
            }
        }

        mDriver = new LintDriver(registry, this);

        mDriver.setAbbreviating(!mShowAll);
        if (!mQuiet) {
            mDriver.addLintListener(new ProgressPrinter());
        }

        mDriver.analyze(files, null /* scope */);

        Collections.sort(mWarnings);

        for (Reporter reporter : mReporters) {
            try {
                reporter.write(mErrorCount, mWarningCount, mWarnings);
            } catch (IOException e) {
                log(e, null);
                System.exit(ERRNO_INVALIDARGS);
            }
        }

        System.exit(mSetExitCode ? (mHasErrors ? ERRNO_ERRORS : 0) : 0);
    }

    /**
     * Converts a relative or absolute command-line argument into an input file.
     *
     * @param filename The filename given as a command-line argument.
     * @return A File matching filename, either absolute or relative to lint.workdir if defined.
     */
    private static File getInArgumentPath(String filename) {
        File file = new File(filename);

        if (!file.isAbsolute()) {
            File workDir = getLintWorkDir();
            if (workDir != null) {
                File file2 = new File(workDir, filename);
                if (file2.exists()) {
                    try {
                        file = file2.getCanonicalFile();
                    } catch (IOException e) {
                        file = file2;
                    }
                }
            }
        }
        return file;
    }

    /**
     * Converts a relative or absolute command-line argument into an output file.
     * <p/>
     * The difference with {@code getInArgumentPath} is that we can't check whether the
     * a relative path turned into an absolute compared to lint.workdir actually exists.
     *
     * @param filename The filename given as a command-line argument.
     * @return A File matching filename, either absolute or relative to lint.workdir if defined.
     */
    private static File getOutArgumentPath(String filename) {
        File file = new File(filename);

        if (!file.isAbsolute()) {
            File workDir = getLintWorkDir();
            if (workDir != null) {
                File file2 = new File(workDir, filename);
                try {
                    file = file2.getCanonicalFile();
                } catch (IOException e) {
                    file = file2;
                }
            }
        }
        return file;
    }


    /**
     * Returns the File corresponding to the system property or the environment variable
     * for {@link #PROP_WORK_DIR}.
     * This property is typically set by the SDK/tools/lint[.bat] wrapper.
     * It denotes the path where the command-line client was originally invoked from
     * and can be used to convert relative input/output paths.
     *
     * @return A new File corresponding to {@link #PROP_WORK_DIR} or null.
     */
    @Nullable
    private static File getLintWorkDir() {
        // First check the Java properties (e.g. set using "java -jar ... -Dname=value")
        String path = System.getProperty(PROP_WORK_DIR);
        if (path == null || path.isEmpty()) {
            // If not found, check environment variables.
            path = System.getenv(PROP_WORK_DIR);
        }
        if (path != null && !path.isEmpty()) {
            return new File(path);
        }
        return null;
    }

    private static void printHelpTopicSuppress() {
        System.out.println(wrap(getSuppressHelp()));
    }

    static String getSuppressHelp() {
        return
            "Lint errors can be suppressed in a variety of ways:\n" +
            "\n" +
            "1. With a @SuppressLint annotation in the Java code\n" +
            "2. With a tools:ignore attribute in the XML file\n" +
            "3. With a lint.xml configuration file in the project\n" +
            "4. With a lint.xml configuration file passed to lint " +
                "via the " + ARG_CONFIG + " flag\n" +
            "5. With the " + ARG_IGNORE + " flag passed to lint.\n" +
            "\n" +
            "To suppress a lint warning with an annotation, add " +
            "a @SuppressLint(\"id\") annotation on the class, method " +
            "or variable declaration closest to the warning instance " +
            "you want to disable. The id can be one or more issue " +
            "id's, such as \"UnusedResources\" or {\"UnusedResources\"," +
            "\"UnusedIds\"}, or it can be \"all\" to suppress all lint " +
            "warnings in the given scope.\n" +
            "\n" +
            "To suppress a lint warning in an XML file, add a " +
            "tools:ignore=\"id\" attribute on the element containing " +
            "the error, or one of its surrounding elements. You also " +
            "need to define the namespace for the tools prefix on the " +
            "root element in your document, next to the xmlns:android " +
            "declaration:\n" +
            "* xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "\n" +
            "To suppress lint warnings with a configuration XML file, " +
            "create a file named lint.xml and place it at the root " +
            "directory of the project in which it applies. (If you " +
            "use the Eclipse plugin's Lint view, you can suppress " +
            "errors there via the toolbar and Eclipse will create the " +
            "lint.xml file for you.).\n" +
            "\n" +
            "The format of the lint.xml file is something like the " +
            "following:\n" +
            "\n" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<lint>\n" +
            "    <!-- Disable this given check in this project -->\n" +
            "    <issue id=\"IconMissingDensityFolder\" severity=\"ignore\" />\n" +
            "\n" +
            "    <!-- Ignore the ObsoleteLayoutParam issue in the given files -->\n" +
            "    <issue id=\"ObsoleteLayoutParam\">\n" +
            "        <ignore path=\"res/layout/activation.xml\" />\n" +
            "        <ignore path=\"res/layout-xlarge/activation.xml\" />\n" +
            "    </issue>\n" +
            "\n" +
            "    <!-- Ignore the UselessLeaf issue in the given file -->\n" +
            "    <issue id=\"UselessLeaf\">\n" +
            "        <ignore path=\"res/layout/main.xml\" />\n" +
            "    </issue>\n" +
            "\n" +
            "    <!-- Change the severity of hardcoded strings to \"error\" -->\n" +
            "    <issue id=\"HardcodedText\" severity=\"error\" />\n" +
            "</lint>\n" +
            "\n" +
            "To suppress lint checks from the command line, pass the " + ARG_IGNORE +  " " +
            "flag with a comma separated list of ids to be suppressed, such as:\n" +
            "\"lint --ignore UnusedResources,UselessLeaf /my/project/path\"\n";
    }

    private void printVersion() {
        String revision = getRevision();
        if (revision != null) {
            System.out.println(String.format("lint: version %1$s", revision));
        } else {
            System.out.println("lint: unknown version");
        }
    }

    @SuppressWarnings("resource") // Eclipse doesn't know about Closeables.closeQuietly
    @Nullable
    String getRevision() {
        File file = findResource("tools" + File.separator +     //$NON-NLS-1$
                                 "source.properties");          //$NON-NLS-1$
        if (file != null && file.exists()) {
            FileInputStream input = null;
            try {
                input = new FileInputStream(file);
                Properties properties = new Properties();
                properties.load(input);

                String revision = properties.getProperty("Pkg.Revision"); //$NON-NLS-1$
                if (revision != null && !revision.isEmpty()) {
                    return revision;
                }
            } catch (IOException e) {
                // Couldn't find or read the version info: just print out unknown below
            } finally {
                Closeables.closeQuietly(input);
            }
        }

        return null;
    }

    private static void displayValidIds(IssueRegistry registry, PrintStream out) {
        List<Category> categories = registry.getCategories();
        out.println("Valid issue categories:");
        for (Category category : categories) {
            out.println("    " + category.getFullName());
        }
        out.println();
        List<Issue> issues = registry.getIssues();
        out.println("Valid issue id's:");
        for (Issue issue : issues) {
            listIssue(out, issue);
        }
    }

    private static void listIssue(PrintStream out, Issue issue) {
        out.print(wrapArg("\"" + issue.getId() + "\": " + issue.getDescription()));
    }

    private static void showIssues(IssueRegistry registry) {
        List<Issue> issues = registry.getIssues();
        List<Issue> sorted = new ArrayList<Issue>(issues);
        Collections.sort(sorted, new Comparator<Issue>() {
            @Override
            public int compare(Issue issue1, Issue issue2) {
                int d = issue1.getCategory().compareTo(issue2.getCategory());
                if (d != 0) {
                    return d;
                }
                d = issue2.getPriority() - issue1.getPriority();
                if (d != 0) {
                    return d;
                }

                return issue1.getId().compareTo(issue2.getId());
            }
        });

        System.out.println("Available issues:\n");
        Category previousCategory = null;
        for (Issue issue : sorted) {
            Category category = issue.getCategory();
            if (!category.equals(previousCategory)) {
                String name = category.getFullName();
                System.out.println(name);
                for (int i = 0, n = name.length(); i < n; i++) {
                    System.out.print('=');
                }
                System.out.println('\n');
                previousCategory = category;
            }

            describeIssue(issue);
            System.out.println();
        }
    }

    private static void describeIssue(Issue issue) {
        System.out.println(issue.getId());
        for (int i = 0; i < issue.getId().length(); i++) {
            System.out.print('-');
        }
        System.out.println();
        System.out.println(wrap("Summary: " + issue.getDescription()));
        System.out.println("Priority: " + issue.getPriority() + " / 10");
        System.out.println("Severity: " + issue.getDefaultSeverity().getDescription());
        System.out.println("Category: " + issue.getCategory().getFullName());

        if (!issue.isEnabledByDefault()) {
            System.out.println("NOTE: This issue is disabled by default!");
            System.out.println(String.format("You can enable it by adding %1$s %2$s", ARG_ENABLE,
                    issue.getId()));
        }

        if (issue.getExplanation() != null) {
            System.out.println();
            System.out.println(wrap(issue.getExplanationAsSimpleText()));
        }
        if (issue.getMoreInfo() != null) {
            System.out.println("More information: " + issue.getMoreInfo());
        }
    }

    static String wrapArg(String explanation) {
        // Wrap arguments such that the wrapped lines are not showing up in the left column
        return wrap(explanation, MAX_LINE_WIDTH, "      ");
    }

    static String wrap(String explanation) {
        return wrap(explanation, MAX_LINE_WIDTH, "");
    }

    static String wrap(String explanation, int lineWidth, String hangingIndent) {
        return SdkUtils.wrap(explanation, lineWidth, hangingIndent);
    }

    private static void printUsage(PrintStream out) {
        // TODO: Look up launcher script name!
        String command = "lint"; //$NON-NLS-1$

        out.println("Usage: " + command + " [flags] <project directories>\n");
        out.println("Flags:\n");

        printUsage(out, new String[] {
            ARG_HELP, "This message.",
            ARG_HELP + " <topic>", "Help on the given topic, such as \"suppress\".",
            ARG_LISTIDS, "List the available issue id's and exit.",
            ARG_VERSION, "Output version information and exit.",
            ARG_EXITCODE, "Set the exit code to " + ERRNO_ERRORS + " if errors are found.",
            ARG_SHOW, "List available issues along with full explanations.",
            ARG_SHOW + " <ids>", "Show full explanations for the given list of issue id's.",

            "", "\nEnabled Checks:",
            ARG_DISABLE + " <list>", "Disable the list of categories or " +
                "specific issue id's. The list should be a comma-separated list of issue " +
                "id's or categories.",
            ARG_ENABLE + " <list>", "Enable the specific list of issues. " +
                "This checks all the default issues plus the specifically enabled issues. The " +
                "list should be a comma-separated list of issue id's or categories.",
            ARG_CHECK + " <list>", "Only check the specific list of issues. " +
                "This will disable everything and re-enable the given list of issues. " +
                "The list should be a comma-separated list of issue id's or categories.",
            ARG_NOWARN1 + ", " + ARG_NOWARN2, "Only check for errors (ignore warnings)",
            ARG_WARNALL, "Check all warnings, including those off by default",
            ARG_ALLERROR, "Treat all warnings as errors",
            ARG_CONFIG + " <filename>", "Use the given configuration file to " +
                    "determine whether issues are enabled or disabled. If a project contains " +
                    "a lint.xml file, then this config file will be used as a fallback.",


            "", "\nOutput Options:",
            ARG_QUIET, "Don't show progress.",
            ARG_FULLPATH, "Use full paths in the error output.",
            ARG_SHOWALL, "Do not truncate long messages, lists of alternate locations, etc.",
            ARG_NOLINES, "Do not include the source file lines with errors " +
                "in the output. By default, the error output includes snippets of source code " +
                "on the line containing the error, but this flag turns it off.",
            ARG_HTML + " <filename>", "Create an HTML report instead. If the filename is a " +
                "directory (or a new filename without an extension), lint will create a " +
                "separate report for each scanned project.",
            ARG_URL + " filepath=url", "Add links to HTML report, replacing local " +
                "path prefixes with url prefix. The mapping can be a comma-separated list of " +
                "path prefixes to corresponding URL prefixes, such as " +
                "C:\\temp\\Proj1=http://buildserver/sources/temp/Proj1.  To turn off linking " +
                "to files, use " + ARG_URL + " " + VALUE_NONE,
            ARG_SIMPLEHTML + " <filename>", "Create a simple HTML report",
            ARG_XML + " <filename>", "Create an XML report instead.",

            "", "\nProject Options:",
            ARG_SOURCES + " <dir>", "Add the given folder (or path) as a source directory for " +
                "the project. Only valid when running lint on a single project.",
            ARG_CLASSES + " <dir>", "Add the given folder (or jar file, or path) as a class " +
                "directory for the project. Only valid when running lint on a single project.",
            ARG_LIBRARIES + " <dir>", "Add the given folder (or jar file, or path) as a class " +
                    "library for the project. Only valid when running lint on a single project.",

            "", "\nExit Status:",
            "0",                                 "Success.",
            Integer.toString(ERRNO_ERRORS),      "Lint errors detected.",
            Integer.toString(ERRNO_USAGE),       "Lint usage.",
            Integer.toString(ERRNO_EXISTS),      "Cannot clobber existing file.",
            Integer.toString(ERRNO_HELP),        "Lint help.",
            Integer.toString(ERRNO_INVALIDARGS), "Invalid command-line argument.",
        });
    }

    private static void printUsage(PrintStream out, String[] args) {
        int argWidth = 0;
        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            argWidth = Math.max(argWidth, arg.length());
        }
        argWidth += 2;
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < argWidth; i++) {
            sb.append(' ');
        }
        String indent = sb.toString();
        String formatString = "%1$-" + argWidth + "s%2$s"; //$NON-NLS-1$

        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            String description = args[i + 1];
            if (arg.isEmpty()) {
                out.println(description);
            } else {
                out.print(wrap(String.format(formatString, arg, description),
                        MAX_LINE_WIDTH, indent));
            }
        }
    }

    @Override
    public void log(
            @NonNull Severity severity,
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        System.out.flush();
        if (!mQuiet) {
            // Place the error message on a line of its own since we're printing '.' etc
            // with newlines during analysis
            System.err.println();
        }
        if (format != null) {
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public IDomParser getDomParser() {
        return new LintCliXmlParser();
    }

    @Override
    public Configuration getConfiguration(@NonNull Project project) {
        return new CliConfiguration(mDefaultConfiguration, project);
    }

    /** File content cache */
    private final Map<File, String> mFileContents = new HashMap<File, String>(100);

    /** Read the contents of the given file, possibly cached */
    private String getContents(File file) {
        String s = mFileContents.get(file);
        if (s == null) {
            s = readFile(file);
            mFileContents.put(file, s);
        }

        return s;
    }

    @Override
    public IJavaParser getJavaParser() {
        return new LombokParser();
    }

    @Override
    public void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @Nullable Location location,
            @NonNull String message,
            @Nullable Object data) {
        assert context.isEnabled(issue);

        if (severity == Severity.IGNORE) {
            return;
        }

        if (severity == Severity.ERROR || severity == Severity.FATAL) {
            mHasErrors = true;
            mErrorCount++;
        } else {
            mWarningCount++;
        }

        Warning warning = new Warning(issue, message, severity, context.getProject(), data);
        mWarnings.add(warning);

        if (location != null) {
            warning.location = location;
            File file = location.getFile();
            if (file != null) {
                warning.file = file;
                warning.path = getDisplayPath(context.getProject(), file);
            }

            Position startPosition = location.getStart();
            if (startPosition != null) {
                int line = startPosition.getLine();
                warning.line = line;
                warning.offset = startPosition.getOffset();
                if (line >= 0) {
                    if (context.file == location.getFile()) {
                        warning.fileContents = context.getContents();
                    }
                    if (warning.fileContents == null) {
                        warning.fileContents = getContents(location.getFile());
                    }

                    if (mShowLines) {
                        // Compute error line contents
                        warning.errorLine = getLine(warning.fileContents, line);
                        if (warning.errorLine != null) {
                            // Replace tabs with spaces such that the column
                            // marker (^) lines up properly:
                            warning.errorLine = warning.errorLine.replace('\t', ' ');
                            int column = startPosition.getColumn();
                            if (column < 0) {
                                column = 0;
                                for (int i = 0; i < warning.errorLine.length(); i++, column++) {
                                    if (!Character.isWhitespace(warning.errorLine.charAt(i))) {
                                        break;
                                    }
                                }
                            }
                            StringBuilder sb = new StringBuilder(100);
                            sb.append(warning.errorLine);
                            sb.append('\n');
                            for (int i = 0; i < column; i++) {
                                sb.append(' ');
                            }

                            boolean displayCaret = true;
                            Position endPosition = location.getEnd();
                            if (endPosition != null) {
                                int endLine = endPosition.getLine();
                                int endColumn = endPosition.getColumn();
                                if (endLine == line && endColumn > column) {
                                    for (int i = column; i < endColumn; i++) {
                                        sb.append('~');
                                    }
                                    displayCaret = false;
                                }
                            }

                            if (displayCaret) {
                                sb.append('^');
                            }
                            sb.append('\n');
                            warning.errorLine = sb.toString();
                        }
                    }
                }
            }
        }
    }

    /** Look up the contents of the given line */
    static String getLine(String contents, int line) {
        int index = getLineOffset(contents, line);
        if (index != -1) {
            return getLineOfOffset(contents, index);
        } else {
            return null;
        }
    }

    static String getLineOfOffset(String contents, int offset) {
        int end = contents.indexOf('\n', offset);
        if (end == -1) {
            end = contents.indexOf('\r', offset);
        }
        return contents.substring(offset, end != -1 ? end : contents.length());
    }


    /** Look up the contents of the given line */
    static int getLineOffset(String contents, int line) {
        int index = 0;
        for (int i = 0; i < line; i++) {
            index = contents.indexOf('\n', index);
            if (index == -1) {
                return -1;
            }
            index++;
        }

        return index;
    }

    @NonNull
    @Override
    public String readFile(@NonNull File file) {
        try {
            return LintUtils.getEncodedString(this, file);
        } catch (IOException e) {
            return ""; //$NON-NLS-1$
        }
    }

    boolean isCheckingSpecificIssues() {
        return mCheck != null;
    }

    private Map<Project, ClassPathInfo> mProjectInfo;

    @Override
    @NonNull
    protected ClassPathInfo getClassPath(@NonNull Project project) {
        ClassPathInfo classPath = super.getClassPath(project);

        if (mClasses == null && mSources == null && mLibraries == null) {
            return classPath;
        }

        ClassPathInfo info;
        if (mProjectInfo == null) {
            mProjectInfo = Maps.newHashMap();
            info = null;
        } else {
            info = mProjectInfo.get(project);
        }

        if (info == null) {
            List<File> sources;
            if (mSources != null) {
                sources = mSources;
            } else {
                sources = classPath.getSourceFolders();
            }
            List<File> classes;
            if (mClasses != null) {
                classes = mClasses;
            } else {
                classes = classPath.getClassFolders();
            }
            List<File> libraries;
            if (mLibraries != null) {
                libraries = mLibraries;
            } else {
                libraries = classPath.getLibraries();
            }

            info = new ClassPathInfo(sources, classes, libraries);
            mProjectInfo.put(project, info);
        }

        return info;
    }

    /**
     * Consult the lint.xml file, but override with the --enable and --disable
     * flags supplied on the command line
     */
    class CliConfiguration extends DefaultConfiguration {
        CliConfiguration(@NonNull Configuration parent, @NonNull Project project) {
            super(Main.this, project, parent);
        }

        CliConfiguration(File lintFile) {
            super(Main.this, null /*project*/, null /*parent*/, lintFile);
        }

        @NonNull
        @Override
        public Severity getSeverity(@NonNull Issue issue) {
            Severity severity = computeSeverity(issue);

            if (mAllErrors && severity != Severity.IGNORE) {
                severity = Severity.ERROR;
            }

            if (mNoWarnings && severity == Severity.WARNING) {
                severity = Severity.IGNORE;
            }

            return severity;
        }

        @NonNull
        @Override
        protected Severity getDefaultSeverity(@NonNull Issue issue) {
            if (mWarnAll) {
                return issue.getDefaultSeverity();
            }

            return super.getDefaultSeverity(issue);
        }

        private Severity computeSeverity(@NonNull Issue issue) {
            Severity severity = super.getSeverity(issue);

            String id = issue.getId();
            if (mSuppress.contains(id)) {
                return Severity.IGNORE;
            }

            if (mEnabled.contains(id) || (mCheck != null && mCheck.contains(id))) {
                // Overriding default
                // Detectors shouldn't be returning ignore as a default severity,
                // but in case they do, force it up to warning here to ensure that
                // it's run
                if (severity == Severity.IGNORE) {
                    severity = issue.getDefaultSeverity();
                    if (severity == Severity.IGNORE) {
                        severity = Severity.WARNING;
                    }
                }

                return severity;
            }

            if (mCheck != null && issue != LINT_ERROR && issue != PARSER_ERROR) {
                return Severity.IGNORE;
            }

            return severity;
        }
    }

    private static class ProgressPrinter implements LintListener {
        @Override
        public void update(
                @NonNull LintDriver lint,
                @NonNull EventType type,
                @Nullable Context context) {
            switch (type) {
                case SCANNING_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    if (lint.getPhase() > 1) {
                        System.out.print(String.format(
                                "\nScanning %1$s (Phase %2$d): ",
                                name,
                                lint.getPhase()));
                    } else {
                        System.out.print(String.format(
                                "\nScanning %1$s: ",
                                name));
                    }
                    break;
                }
                case SCANNING_LIBRARY_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    System.out.print(String.format(
                            "\n         - %1$s: ",
                            name));
                    break;
                }
                case SCANNING_FILE:
                    System.out.print('.');
                    break;
                case NEW_PHASE:
                    // Ignored for now: printing status as part of next project's status
                    break;
                case CANCELED:
                case COMPLETED:
                    System.out.println();
                    break;
                case STARTING:
                    // Ignored for now
                    break;
            }
        }
    }

    /**
     * Given a file, it produces a cleaned up path from the file.
     * This will clean up the path such that
     * <ul>
     *   <li>  {@code foo/./bar} becomes {@code foo/bar}
     *   <li>  {@code foo/bar/../baz} becomes {@code foo/baz}
     * </ul>
     *
     * Unlike {@link File#getCanonicalPath()} however, it will <b>not</b> attempt
     * to make the file canonical, such as expanding symlinks and network mounts.
     *
     * @param file the file to compute a clean path for
     * @return the cleaned up path
     */
    @VisibleForTesting
    @NonNull
    static String getCleanPath(@NonNull File file) {
        String path = file.getPath();
        StringBuilder sb = new StringBuilder(path.length());

        if (path.startsWith(File.separator)) {
            sb.append(File.separator);
        }
        elementLoop:
        for (String element : Splitter.on(File.separatorChar).omitEmptyStrings().split(path)) {
            if (element.equals(".")) {          //$NON-NLS-1$
                continue;
            } else if (element.equals("..")) {  //$NON-NLS-1$
                if (sb.length() > 0) {
                    for (int i = sb.length() - 1; i >= 0; i--) {
                        char c = sb.charAt(i);
                        if (c == File.separatorChar) {
                            sb.setLength(i == 0 ? 1 : i);
                            continue elementLoop;
                        }
                    }
                    sb.setLength(0);
                    continue;
                }
            }

            if (sb.length() > 1) {
                sb.append(File.separatorChar);
            } else if (sb.length() > 0 && sb.charAt(0) != File.separatorChar) {
                sb.append(File.separatorChar);
            }
            sb.append(element);
        }
        if (path.endsWith(File.separator) && sb.length() > 0
                && sb.charAt(sb.length() - 1) != File.separatorChar) {
            sb.append(File.separator);
        }

        return sb.toString();
    }

    String getDisplayPath(Project project, File file) {
        String path = file.getPath();
        if (!mFullPath && path.startsWith(project.getReferenceDir().getPath())) {
            int chop = project.getReferenceDir().getPath().length();
            if (path.length() > chop && path.charAt(chop) == File.separatorChar) {
                chop++;
            }
            path = path.substring(chop);
            if (path.isEmpty()) {
                path = file.getName();
            }
        } else if (mFullPath) {
            path = getCleanPath(file.getAbsoluteFile());
        }

        return path;
    }

    /** Returns whether all warnings are enabled, including those disabled by default */
    boolean isAllEnabled() {
        return mWarnAll;
    }

    /** Returns the issue registry used by this client */
    IssueRegistry getRegistry() {
        return mRegistry;
    }

    /** Returns the driver running the lint checks */
    LintDriver getDriver() {
        return mDriver;
    }

    /** Returns the configuration used by this client */
    Configuration getConfiguration() {
        return mDefaultConfiguration;
    }

    /** Returns true if the given issue has been explicitly disabled */
    boolean isSuppressed(Issue issue) {
        return mSuppress.contains(issue.getId());
    }
}
