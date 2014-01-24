/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.annotations.NonNull
import com.android.annotations.Nullable;
import com.android.builder.model.LintOptions
import com.android.tools.lint.HtmlReporter
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.Reporter
import com.android.tools.lint.TextReporter
import com.android.tools.lint.XmlReporter
import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile

import static com.android.SdkConstants.DOT_XML

public class LintOptionsImpl implements LintOptions, Serializable {
    public static final String STDOUT = "stdout"
    private static final long serialVersionUID = 1L;

    @Input
    private Set<String> disable = Sets.newHashSet()
    @Input
    private Set<String> enable = Sets.newHashSet()
    @Input
    private Set<String> check = Sets.newHashSet()
    @Input
    private boolean abortOnError = true
    @Input
    private boolean absolutePaths = true
    @Input
    private boolean noLines
    @Input
    private boolean quiet = true
    @Input
    private boolean checkAllWarnings
    @Input
    private boolean ignoreWarnings
    @Input
    private boolean warningsAsErrors
    @Input
    private boolean showAll
    @Input
    private boolean checkReleaseBuilds = true;
    @InputFile
    private File lintConfig
    @Input
    private boolean textReport
    @OutputFile
    private File textOutput
    @Input
    private boolean htmlReport = true
    @OutputFile
    private File htmlOutput
    @Input
    private boolean xmlReport = true
    @OutputFile
    private File xmlOutput

    public LintOptionsImpl() {
    }

    public LintOptionsImpl(
            @Nullable Set<String> disable,
            @Nullable Set<String> enable,
            @Nullable Set<String> check,
            @Nullable File lintConfig,
            boolean textReport,
            @Nullable File textOutput,
            boolean htmlReport,
            @Nullable File htmlOutput,
            boolean xmlReport,
            @Nullable File xmlOutput,
            boolean abortOnError,
            boolean absolutePaths,
            boolean noLines,
            boolean quiet,
            boolean checkAllWarnings,
            boolean ignoreWarnings,
            boolean warningsAsErrors,
            boolean showAll,
            boolean checkReleaseBuilds) {
        this.disable = disable
        this.enable = enable
        this.check = check
        this.lintConfig = lintConfig
        this.textReport = textReport
        this.textOutput = textOutput
        this.htmlReport = htmlReport
        this.htmlOutput = htmlOutput
        this.xmlReport = xmlReport
        this.xmlOutput = xmlOutput
        this.abortOnError = abortOnError
        this.absolutePaths = absolutePaths
        this.noLines = noLines
        this.quiet = quiet
        this.checkAllWarnings = checkAllWarnings
        this.ignoreWarnings = ignoreWarnings
        this.warningsAsErrors = warningsAsErrors
        this.showAll = showAll
        this.checkReleaseBuilds = checkReleaseBuilds
    }

    @NonNull
    static LintOptions create(@NonNull LintOptions source) {
        return new LintOptionsImpl(
                source.getDisable(),
                source.getEnable(),
                source.getCheck(),
                source.getLintConfig(),
                source.getTextReport(),
                source.getTextOutput(),
                source.getHtmlReport(),
                source.getHtmlOutput(),
                source.getXmlReport(),
                source.getXmlOutput(),
                source.isAbortOnError(),
                source.isAbsolutePaths(),
                source.isNoLines(),
                source.isQuiet(),
                source.isCheckAllWarnings(),
                source.isIgnoreWarnings(),
                source.isWarningsAsErrors(),
                source.isShowAll(),
                source.isCheckReleaseBuilds()
        )
    }

    /**
     * Returns the set of issue id's to suppress. Callers are allowed to modify this collection.
     */
    @NonNull
    public Set<String> getDisable() {
        return disable
    }

    /**
     * Sets the set of issue id's to suppress. Callers are allowed to modify this collection.
     * Note that these ids add to rather than replace the given set of ids.
     */
    public void setDisable(@Nullable Set<String> ids) {
        disable.addAll(ids)
    }

    /**
     * Returns the set of issue id's to enable. Callers are allowed to modify this collection.
     * To enable a given issue, add the {@link com.android.tools.lint.detector.api.Issue#getId()} to the returned set.
     */
    @NonNull
    public Set<String> getEnable() {
        return enable
    }

    /**
     * Sets the set of issue id's to enable. Callers are allowed to modify this collection.
     * Note that these ids add to rather than replace the given set of ids.
     */
    public void setEnable(@Nullable Set<String> ids) {
        enable.addAll(ids)
    }

    /**
     * Returns the exact set of issues to check, or null to run the issues that are enabled
     * by default plus any issues enabled via {@link #getEnable} and without issues disabled
     * via {@link #getDisable}. If non-null, callers are allowed to modify this collection.
     */
    @Nullable
    public Set<String> getCheck() {
        return check
    }

    /**
     * Sets the <b>exact</b> set of issues to check.
     * @param ids the set of issue id's to check
     */
    public void setCheck(@Nullable Set<String> ids) {
        check.addAll(ids)
    }

    /** Whether lint should set the exit code of the process if errors are found */
    public boolean isAbortOnError() {
        return this.abortOnError
    }

    /** Sets whether lint should set the exit code of the process if errors are found */
    public void setAbortOnError(boolean abortOnError) {
        this.abortOnError = abortOnError
    }

    /**
     * Whether lint should display full paths in the error output. By default the paths
     * are relative to the path lint was invoked from.
     */
    public boolean isAbsolutePaths() {
        return absolutePaths
    }

    /**
     * Sets whether lint should display full paths in the error output. By default the paths
     * are relative to the path lint was invoked from.
     */
    public void setAbsolutePaths(boolean absolutePaths) {
        this.absolutePaths = absolutePaths
    }

    /**
     * Whether lint should include the source lines in the output where errors occurred
     * (true by default)
     */
    public boolean isNoLines() {
        return this.noLines
    }

    /**
     * Sets whether lint should include the source lines in the output where errors occurred
     * (true by default)
     */
    public void setNoLines(boolean noLines) {
        this.noLines = noLines
    }

    /**
     * Returns whether lint should be quiet (for example, not show progress dots for each analyzed
     * file)
     */
    public boolean isQuiet() {
        return quiet
    }

    /**
     * Sets whether lint should be quiet (for example, not show progress dots for each analyzed
     * file)
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet
    }

    /** Returns whether lint should check all warnings, including those off by default */
    public boolean isCheckAllWarnings() {
        return checkAllWarnings
    }

    /** Sets whether lint should check all warnings, including those off by default */
    public void setCheckAllWarnings(boolean warnAll) {
        this.checkAllWarnings = warnAll
    }

    /** Returns whether lint will only check for errors (ignoring warnings) */
    public boolean isIgnoreWarnings() {
        return ignoreWarnings
    }

    /** Sets whether lint will only check for errors (ignoring warnings) */
    public void setIgnoreWarnings(boolean noWarnings) {
        this.ignoreWarnings = noWarnings
    }

    /** Returns whether lint should treat all warnings as errors */
    public boolean isWarningsAsErrors() {
        return warningsAsErrors
    }

    /** Sets whether lint should treat all warnings as errors */
    public void setWarningsAsErrors(boolean allErrors) {
        this.warningsAsErrors = allErrors
    }

    /**
     * Returns whether lint should include all output (e.g. include all alternate
     * locations, not truncating long messages, etc.)
     */
    public boolean isShowAll() {
        return showAll
    }

    /**
     * Sets whether lint should include all output (e.g. include all alternate
     * locations, not truncating long messages, etc.)
     */
    public void setShowAll(boolean showAll) {
        this.showAll = showAll
    }

    @Override
    public boolean isCheckReleaseBuilds() {
        return checkReleaseBuilds;
    }

    public void setCheckReleaseBuilds(boolean checkReleaseBuilds) {
        this.checkReleaseBuilds = checkReleaseBuilds
    }

    /**
     * Returns the default configuration file to use as a fallback
     */
    public File getLintConfig() {
        return lintConfig
    }

    @Override
    boolean getTextReport() {
        return textReport
    }

    void setTextReport(boolean textReport) {
        this.textReport = textReport
    }

    void setTextOutput(@NonNull File textOutput) {
        this.textOutput = textOutput
    }

    void setHtmlReport(boolean htmlReport) {
        this.htmlReport = htmlReport
    }

    void setHtmlOutput(@NonNull File htmlOutput) {
        this.htmlOutput = htmlOutput
    }

    void setXmlReport(boolean xmlReport) {
        this.xmlReport = xmlReport
    }

    void setXmlOutput(@NonNull File xmlOutput) {
        this.xmlOutput = xmlOutput
    }

    @Override
    File getTextOutput() {
        return textOutput
    }

    @Override
    boolean getHtmlReport() {
        return htmlReport
    }

    @Override
    File getHtmlOutput() {
        return htmlOutput
    }

    @Override
    boolean getXmlReport() {
        return xmlReport
    }

    @Override
    File getXmlOutput() {
        return xmlOutput
    }

    /**
     * Sets the default config file to use as a fallback. This corresponds to a {@code lint.xml}
     * file with severities etc to use when a project does not have more specific information.
     */
    public void setLintConfig(@NonNull File lintConfig) {
        this.lintConfig = lintConfig
    }

    public void syncTo(
            @NonNull LintCliClient client,
            @NonNull LintCliFlags flags,
            @Nullable String variantName,
            @Nullable Project project,
            boolean report) {
        if (disable != null) {
            flags.getSuppressedIds().addAll(disable)
        }
        if (enable != null) {
            flags.getEnabledIds().addAll(enable)
        }
        if (check != null && !check.isEmpty()) {
            flags.setExactCheckedIds(check)
        }
        flags.setSetExitCode(this.abortOnError)
        flags.setFullPath(absolutePaths)
        flags.setShowSourceLines(!noLines)
        flags.setQuiet(quiet)
        flags.setCheckAllWarnings(checkAllWarnings)
        flags.setIgnoreWarnings(ignoreWarnings)
        flags.setWarningsAsErrors(warningsAsErrors)
        flags.setShowEverything(showAll)
        flags.setDefaultConfiguration(lintConfig)

        if (report || flags.isFatalOnly()) {
            if (textReport || flags.isFatalOnly()) {
                File output = textOutput
                if (output == null) {
                    output = new File(STDOUT)
                } else if (!output.isAbsolute() && !isStdOut(output)) {
                    output = project.file(output.getPath())
                }
                output = validateOutputFile(output)

                Writer writer
                File file = null
                boolean closeWriter
                if (isStdOut(output)) {
                    writer = new PrintWriter(System.out, true)
                    closeWriter = false
                } else {
                    file = output
                    try {
                        writer = new BufferedWriter(new FileWriter(output))
                    } catch (IOException e) {
                        throw new GradleException("Text invalid argument.", e)
                    }
                    closeWriter = true
                }
                flags.getReporters().add(new TextReporter(client, flags, file, writer,
                        closeWriter))
            }
            if (xmlReport) {
                File output = xmlOutput
                if (output == null || flags.isFatalOnly()) {
                    output = createOutputPath(project, variantName, DOT_XML, flags.isFatalOnly())
                } else if (!output.isAbsolute()) {
                    output = project.file(output.getPath())
                }
                output = validateOutputFile(output)
                try {
                    flags.getReporters().add(new XmlReporter(client, output))
                } catch (IOException e) {
                    throw new GradleException("XML invalid argument.", e)
                }
            }
            if (htmlReport) {
                File output = htmlOutput
                if (output == null || flags.isFatalOnly()) {
                    output = createOutputPath(project, variantName, ".html", flags.isFatalOnly())
                } else if (!output.isAbsolute()) {
                    output = project.file(output.getPath())
                }
                output = validateOutputFile(output)
                try {
                    flags.getReporters().add(new HtmlReporter(client, output))
                } catch (IOException e) {
                    throw new GradleException("HTML invalid argument.", e)
                }
            }
        }
    }

    private static boolean isStdOut(@NonNull File output) {
        return STDOUT.equals(output.getPath())
    }

    @NonNull
    private static File validateOutputFile(@NonNull File output) {
        if (isStdOut(output)) {
            return output
        }

        File parent = output.parentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }

        output = output.getAbsoluteFile()
        if (output.exists()) {
            boolean delete = output.delete()
            if (!delete) {
                throw new GradleException("Could not delete old " + output)
            }
        }
        if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
            throw new GradleException("Cannot write output file " + output)
        }

        return output
    }

    private static File createOutputPath(
            @NonNull Project project,
            @NonNull String variantName,
            @NonNull String extension,
            boolean fatalOnly) {
        StringBuilder base = new StringBuilder()
        base.append("lint-results")
        if (variantName != null) {
            base.append("-")
            base.append(variantName)
        }
        if (fatalOnly) {
            base.append("-fatal")
        }
        base.append(extension)
        return new File(project.buildDir, base.toString())
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void check(String id) {
        check.add(id)
    }

    public void check(String... ids) {
        check.addAll(ids)
    }

    public void enable(String id) {
        enable.add(id)
    }

    public void enable(String... ids) {
        enable.addAll(ids)
    }

    public void disable(String id) {
        disable.add(id)
    }

    public void disable(String... ids) {
        disable.addAll(ids)
    }

    // For textOutput 'stdout' (normally a file)
    void textOutput(String textOutput) {
        this.textOutput = new File(textOutput)
    }

    // For textOutput file()
    void textOutput(File textOutput) {
        this.textOutput = textOutput;
    }
}
