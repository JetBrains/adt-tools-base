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
package com.android.tools.lint.client.api;

import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.FieldGetterDetector;
import com.android.tools.lint.checks.MathDetector;
import com.android.tools.lint.checks.ObsoleteLayoutParamsDetector;
import com.android.tools.lint.checks.SdCardDetector;
import com.android.tools.lint.checks.TypoDetector;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public class DefaultConfigurationTest extends AbstractCheckTest {

    public void test() throws Exception {
        DefaultConfiguration configuration = getConfiguration(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lint>\n"
                + "    <issue id=\"ObsoleteLayoutParam\">\n"
                + "        <ignore path=\"res/layout-xlarge/activation.xml\" />\n"
                + "    </issue>\n"
                + "    <issue id=\"FloatMath\" severity=\"ignore\" />\n"
                + "    <issue id=\"FieldGetter\" severity=\"error\" />\n"
                + "    <issue id=\"SdCardPath,ContentDescription\" severity=\"ignore\" />"
                + "    <issue id=\"NewApi\">\n"
                + "        <ignore path=\"res/layout-xlarge\" />\n"
                + "    </issue>\n"
                + "</lint>");
        assertTrue(configuration.isEnabled(ObsoleteLayoutParamsDetector.ISSUE));
        assertFalse(configuration.isEnabled(SdCardDetector.ISSUE));
        assertFalse(configuration.isEnabled(MathDetector.ISSUE));
        assertFalse(configuration.isEnabled(AccessibilityDetector.ISSUE));
        assertEquals(Severity.IGNORE, configuration.getSeverity(AccessibilityDetector.ISSUE));
        assertEquals(Severity.WARNING, AccessibilityDetector.ISSUE.getDefaultSeverity());
        assertEquals(Severity.WARNING, FieldGetterDetector.ISSUE.getDefaultSeverity());
        assertEquals(Severity.ERROR, configuration.getSeverity(FieldGetterDetector.ISSUE));
        assertEquals(Severity.IGNORE, configuration.getSeverity(MathDetector.ISSUE));
    }

    public void testPathIgnore() throws Exception {
        File projectDir = getProjectDir(null,
                "res/layout/onclick.xml=>res/layout/onclick.xml",
                "res/layout/onclick.xml=>res/layout-xlarge/onclick.xml",
                "res/layout/onclick.xml=>res/layout-xlarge/activation.xml"
        );
        LintClient client = new TestLintClient();
        Project project = Project.create(client, projectDir, projectDir);
        LintDriver driver = new LintDriver(new BuiltinIssueRegistry(), client);
        File plainFile = new File(projectDir,
                "res" + File.separator + "layout" + File.separator + "onclick.xml");
        assertTrue(plainFile.exists());
        File largeFile = new File(projectDir,
                "res" + File.separator + "layout-xlarge" + File.separator + "onclick.xml");
        assertTrue(largeFile.exists());
        File windowsFile = new File(projectDir,
                "res" + File.separator + "layout-xlarge" + File.separator + "activation.xml");
        assertTrue(windowsFile.exists());
        Context plainContext = new Context(driver, project, project, plainFile);
        Context largeContext = new Context(driver, project, project, largeFile);
        Context windowsContext = new Context(driver, project, project, windowsFile);
        Location plainLocation = Location.create(plainFile);
        Location largeLocation = Location.create(largeFile);
        Location windowsLocation = Location.create(windowsFile);

        assertEquals(Severity.WARNING, ObsoleteLayoutParamsDetector.ISSUE.getDefaultSeverity());
        assertEquals(Severity.ERROR, ApiDetector.UNSUPPORTED.getDefaultSeverity());

        DefaultConfiguration configuration = getConfiguration(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lint>\n"
                + "    <issue id=\"ObsoleteLayoutParam\">\n"
                + "        <ignore path=\"res/layout-xlarge/onclick.xml\" />\n"
                + "        <ignore path=\"res\\layout-xlarge\\activation.xml\" />\n"
                + "    </issue>\n"
                + "    <issue id=\"NewApi\">\n"
                + "        <ignore path=\"res/layout-xlarge\" />\n"
                + "    </issue>\n"
                + "</lint>");

        assertFalse(configuration
                .isIgnored(plainContext, ApiDetector.UNSUPPORTED, plainLocation, "", null));
        assertFalse(configuration
                .isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, plainLocation,
                        "", null));

        assertTrue(configuration
                .isIgnored(windowsContext, ObsoleteLayoutParamsDetector.ISSUE, windowsLocation,
                        "", null));
        assertTrue(
                configuration
                        .isIgnored(largeContext, ApiDetector.UNSUPPORTED, largeLocation, "", null));
        assertTrue(configuration
                .isIgnored(largeContext, ObsoleteLayoutParamsDetector.ISSUE, largeLocation,
                        "", null));
    }

    public void testWriteLintXml() throws Exception {
        DefaultConfiguration configuration = getConfiguration(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lint>\n"
                + "  <issue id=\"ObsoleteLayoutParam\">\n"
                + "      <ignore path=\"res/layout-xlarge/activation.xml\" />\n"
                + "      <ignore path=\"res\\layout-xlarge\\activation2.xml\" />\n"
                + "  </issue>\n"
                + "  <issue id=\"FloatMath\" severity=\"ignore\" />\n"
                + "  <issue id=\"SdCardPath\" severity=\"ignore\" />"
                + "</lint>");
        configuration.startBulkEditing();;
        configuration.setSeverity(TypoDetector.ISSUE, Severity.ERROR);
        configuration.ignore(TypoDetector.ISSUE, new File("foo/bar/Baz.java"));
        configuration.finishBulkEditing();
        String updated = Files.toString(configuration.getConfigFile(), Charsets.UTF_8);
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lint>\n"
                + "    <issue id=\"FloatMath\" severity=\"ignore\" />\n"
                + "    <issue id=\"ObsoleteLayoutParam\">\n"
                + "        <ignore path=\"res/layout-xlarge/activation.xml\" />\n"
                + "        <ignore path=\"res/layout-xlarge/activation2.xml\" />\n"
                + "    </issue>\n"
                + "    <issue id=\"SdCardPath\" severity=\"ignore\" />\n"
                + "    <issue id=\"Typos\" severity=\"error\">\n"
                + "        <ignore path=\"foo/bar/Baz.java\" />\n"
                + "    </issue>\n"
                + "</lint>",
                updated);
    }

    private DefaultConfiguration getConfiguration(String xml) throws IOException {
        LintClient client = new TestLintClient();
        File lintFile = File.createTempFile("lintconfig", ".xml");
        Files.write(xml, lintFile, Charsets.UTF_8);
        return DefaultConfiguration.create(client, lintFile);
    }

    @Override
    protected Detector getDetector() {
        fail("Not used from this unit test");
        return null;
    }
}
