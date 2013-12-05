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

package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.LintGradleClient
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.tools.lint.HtmlReporter
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.Reporter
import com.android.tools.lint.Warning
import com.android.tools.lint.XmlReporter
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Severity
import com.google.common.collect.Maps
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import static com.android.SdkConstants.DOT_XML

public class Lint extends DefaultTask {
    @NonNull private BasePlugin mPlugin
    @Nullable private File mConfigFile
    @Nullable private File mHtmlOutput
    @Nullable private File mXmlOutput
    @Nullable private String mVariantName
    private boolean mQuiet = true

    public void setPlugin(@NonNull BasePlugin plugin) {
        mPlugin = plugin
    }

    public void setVariantName(@NonNull String variantName) {
        mVariantName = variantName
    }

    public void setQuiet() {
        mQuiet = true
    }

    public void setConfig(@NonNull File configFile) {
        mConfigFile = configFile
    }

    public void setHtmlOutput(@NonNull File htmlOutput) {
        mHtmlOutput = htmlOutput
    }

    public void setXmlOutput(@NonNull File xmlOutput) {
        mXmlOutput = xmlOutput
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    public void lint() {
        def modelProject = createAndroidProject(mPlugin.getProject())
        if (mVariantName != null) {
            lintSingleVariant(modelProject, mVariantName)
        } else {
            lintAllVariants(modelProject)
        }
    }

    /**
     * Runs lint individually on all the variants, and then compares the results
     * across variants and reports these
     */
    public void lintAllVariants(@NonNull AndroidProject modelProject) {
        Map<Variant,List<Warning>> warningMap = Maps.newHashMap()
        for (Variant variant : modelProject.getVariants()) {
            try {
                List<Warning> warnings = runLint(modelProject, variant.getName())
                warningMap.put(variant, warnings)
            } catch (IOException e) {
                throw new GradleException("Invalid arguments.", e)
            }
        }

        // Compute error matrix
        for (Map.Entry<Variant,List<Warning>> entry : warningMap.entrySet()) {
            def variant = entry.getKey()
            def warnings = entry.getValue()
            println "Ran lint on variant " + variant.getName() + ": " + warnings.size() +
                    " issues found"
        }

        List<Warning> mergedWarnings = LintGradleClient.merge(warningMap, modelProject)
        int errorCount = 0
        int warningCount = 0
        for (Warning warning : mergedWarnings) {
            if (warning.severity == Severity.ERROR || warning.severity == Severity.FATAL) {
                errorCount++
            } else if (warning.severity == Severity.WARNING) {
                warningCount++
            }
        }

        IssueRegistry registry = new BuiltinIssueRegistry()
        LintCliFlags flags = new LintCliFlags()
        LintGradleClient client = new LintGradleClient(registry, flags, mPlugin, modelProject,
                null)
        configureReporters(client, flags, null)
        for (Reporter reporter : flags.getReporters()) {
            reporter.write(errorCount, warningCount, mergedWarnings)
        }
    }

    /**
     * Runs lint on a single specified variant
     */
    public void lintSingleVariant(@NonNull AndroidProject modelProject, String variantName) {
        runLint(modelProject, variantName)
    }

    /** Runs lint on the given variant and returns the set of warnings */
    private List<Warning> runLint(
            @NonNull AndroidProject modelProject,
            @NonNull String variantName) {
        IssueRegistry registry = new BuiltinIssueRegistry()
        LintCliFlags flags = new LintCliFlags()
        LintGradleClient client = new LintGradleClient(registry, flags, mPlugin, modelProject,
                variantName)

        // Configure Reporters
        configureReporters(client, flags, variantName)

        // Flags
        if (mQuiet) {
            flags.setQuiet(true)
        }
        if (mConfigFile != null) {
            flags.setDefaultConfiguration(client.createConfigurationFromFile(mConfigFile))
        }

        // Finally perform lint run
        try {
            return client.run(registry)
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e)
        }
    }

    private void configureReporters(@NonNull LintGradleClient client, @NonNull LintCliFlags flags,
            @Nullable String variantName) {
        StringBuilder base = new StringBuilder()
        base.append("lint-results/")
        if (variantName != null) {
            base.append(variantName)
            base.append("/")
        }
        base.append("lint-results")
        if (variantName != null) {
            base.append("-")
            base.append(variantName)
        }
        File htmlOutput = mHtmlOutput
        File xmlOutput = mXmlOutput
        if (htmlOutput == null) {
            htmlOutput = project.file(base.toString() + ".html")
            File parent = htmlOutput.parentFile
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        if (xmlOutput == null) {
            xmlOutput = project.file(base.toString() + DOT_XML)
            File parent = xmlOutput.parentFile
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        htmlOutput = htmlOutput.getAbsoluteFile()
        if (htmlOutput.exists()) {
            boolean delete = htmlOutput.delete()
            if (!delete) {
                throw new GradleException("Could not delete old " + htmlOutput)
            }
        }
        if (htmlOutput.getParentFile() != null && !htmlOutput.getParentFile().canWrite()) {
            throw new GradleException("Cannot write HTML output file " + htmlOutput)
        }
        try {
            flags.getReporters().add(new HtmlReporter(client, htmlOutput))
        } catch (IOException e) {
            throw new GradleException("HTML invalid argument.", e)
        }

        xmlOutput = xmlOutput.getAbsoluteFile()
        if (xmlOutput.exists()) {
            boolean delete = xmlOutput.delete()
            if (!delete) {
                throw new GradleException("Could not delete old " + xmlOutput)
            }
        }
        if (xmlOutput.getParentFile() != null && !xmlOutput.getParentFile().canWrite()) {
            throw new GradleException("Cannot write XML output file " + xmlOutput)
        }
        try {
            flags.getReporters().add(new XmlReporter(client, xmlOutput))
        } catch (IOException e) {
            throw new GradleException("XML invalid argument.", e)
        }

        List<Reporter> reporters = flags.getReporters()
        if (reporters.isEmpty()) {
            throw new GradleException("No reporter specified.")
        }

        Map<String, String> map = new HashMap<String, String>() {{
            put("", "file://")
        }}
        for (Reporter reporter : reporters) {
            reporter.setUrlMap(map)
        }
    }

    private static AndroidProject createAndroidProject(@NonNull Project gradleProject) {
        String modelName = AndroidProject.class.getName()
        ModelBuilder builder = new ModelBuilder()
        return (AndroidProject) builder.buildAll(modelName, gradleProject)
    }
}
