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

package com.android.tools.lint;

import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.ManifestOrderDetector;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.w3c.dom.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("javadoc")
public class XmlReporterTest extends AbstractCheckTest {
    public void test() throws Exception {
        File file = new File(getTargetDir(), "report");
        try {
            Main client = new Main() {
                @Override
                String getRevision() {
                    return "unittest"; // Hardcode version to keep unit test output stable
                }
            };
            file.getParentFile().mkdirs();
            XmlReporter reporter = new XmlReporter(client, file);
            Project project = Project.create(client, new File("/foo/bar/Foo"),
                    new File("/foo/bar/Foo"));

            Warning warning1 = new Warning(ManifestOrderDetector.USES_SDK,
                    "<uses-sdk> tag should specify a target API level (the highest verified " +
                    "version; when running on later versions, compatibility behaviors may " +
                    "be enabled) with android:targetSdkVersion=\"?\"",
                    Severity.WARNING, project, null);
            warning1.line = 6;
            warning1.file = new File("/foo/bar/Foo/AndroidManifest.xml");
            warning1.errorLine = "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n";
            warning1.path = "AndroidManifest.xml";
            warning1.location = Location.create(warning1.file,
                    new DefaultPosition(6, 4, 198), new DefaultPosition(6, 42, 236));

            Warning warning2 = new Warning(HardcodedValuesDetector.ISSUE,
                    "[I18N] Hardcoded string \"Fooo\", should use @string resource",
                    Severity.WARNING, project, null);
            warning2.line = 11;
            warning2.file = new File("/foo/bar/Foo/res/layout/main.xml");
            warning2.errorLine = " (java.lang.String)         android:text=\"Fooo\" />\n" +
                          "        ~~~~~~~~~~~~~~~~~~~\n";
            warning2.path = "res/layout/main.xml";
            warning2.location = Location.create(warning2.file,
                    new DefaultPosition(11, 8, 377), new DefaultPosition(11, 27, 396));

            List<Warning> warnings = new ArrayList<Warning>();
            warnings.add(warning1);
            warnings.add(warning2);

            reporter.write(0, 2, warnings);

            String report = Files.toString(file, Charsets.UTF_8);
            assertEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<issues format=\"3\" by=\"lint unittest\">\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"UsesMinSdkAttributes\"\n" +
                "        severity=\"Warning\"\n" +
                "        message=\"&lt;uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=&quot;?&quot;\"\n" +
                "        category=\"Correctness\"\n" +
                "        priority=\"9\"\n" +
                "        summary=\"Checks that the minimum SDK and target SDK attributes are defined\"\n" +
                "        explanation=\"The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for.)\"\n" +
                "        url=\"http://developer.android.com/guide/topics/manifest/uses-sdk-element.html\"\n" +
                "        errorLine1=\"    &lt;uses-sdk android:minSdkVersion=&quot;8&quot; />\"\n" +
                "        errorLine2=\"    ^\">\n" +
                "        <location\n" +
                "            file=\"AndroidManifest.xml\"\n" +
                "            line=\"7\"\n" +
                "            column=\"5\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"HardcodedText\"\n" +
                "        severity=\"Warning\"\n" +
                "        message=\"[I18N] Hardcoded string &quot;Fooo&quot;, should use @string resource\"\n" +
                "        category=\"Internationalization\"\n" +
                "        priority=\"5\"\n" +
                "        summary=\"Looks for hardcoded text attributes which should be converted to resource lookup\"\n" +
                "        explanation=\"Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
                "\n" +
                "* When creating configuration variations (for example for landscape or portrait)you have to repeat the actual text (and keep it up to date when making changes)\n" +
                "\n" +
                "* The application cannot be translated to other languages by just adding new translations for existing string resources.\n" +
                "\n" +
                "In Eclipse there is a quickfix to automatically extract this hardcoded string into a resource lookup.\"\n" +
                "        errorLine1=\" (java.lang.String)         android:text=&quot;Fooo&quot; />\"\n" +
                "        errorLine2=\"        ~~~~~~~~~~~~~~~~~~~\">\n" +
                "        <location\n" +
                "            file=\"res/layout/main.xml\"\n" +
                "            line=\"12\"\n" +
                "            column=\"9\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "</issues>\n",
                report);

            // Make sure the XML is valid
            Document document = new PositionXmlParser().parse(report);
            assertNotNull(document);
            assertEquals(2, document.getElementsByTagName("issue").getLength());
        } finally {
            file.delete();
        }
    }

    public void testFullPaths() throws Exception {
        File file = new File(getTargetDir(), "report");
        try {
            Main client = new Main() {
                @Override
                String getRevision() {
                    return "unittest"; // Hardcode version to keep unit test output stable
                }
            };
            client.mFullPath = true;

            file.getParentFile().mkdirs();
            XmlReporter reporter = new XmlReporter(client, file);
            Project project = Project.create(client, new File("/foo/bar/Foo"),
                    new File("/foo/bar/Foo"));

            Warning warning1 = new Warning(ManifestOrderDetector.USES_SDK,
                    "<uses-sdk> tag should specify a target API level (the highest verified " +
                    "version; when running on later versions, compatibility behaviors may " +
                    "be enabled) with android:targetSdkVersion=\"?\"",
                    Severity.WARNING, project, null);
            warning1.line = 6;
            warning1.file = new File("/foo/bar/../Foo/AndroidManifest.xml");
            warning1.errorLine = "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n";
            warning1.path = "AndroidManifest.xml";
            warning1.location = Location.create(warning1.file,
                    new DefaultPosition(6, 4, 198), new DefaultPosition(6, 42, 236));

            Warning warning2 = new Warning(HardcodedValuesDetector.ISSUE,
                    "[I18N] Hardcoded string \"Fooo\", should use @string resource",
                    Severity.WARNING, project, null);
            warning2.line = 11;
            warning2.file = new File("/foo/bar/Foo/res/layout/main.xml");
            warning2.errorLine = " (java.lang.String)         android:text=\"Fooo\" />\n" +
                          "        ~~~~~~~~~~~~~~~~~~~\n";
            warning2.path = "res/layout/main.xml";
            warning2.location = Location.create(warning2.file,
                    new DefaultPosition(11, 8, 377), new DefaultPosition(11, 27, 396));

            List<Warning> warnings = new ArrayList<Warning>();
            warnings.add(warning1);
            warnings.add(warning2);

            reporter.write(0, 2, warnings);

            String report = Files.toString(file, Charsets.UTF_8);
            assertEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<issues format=\"3\" by=\"lint unittest\">\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"UsesMinSdkAttributes\"\n" +
                "        severity=\"Warning\"\n" +
                "        message=\"&lt;uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=&quot;?&quot;\"\n" +
                "        category=\"Correctness\"\n" +
                "        priority=\"9\"\n" +
                "        summary=\"Checks that the minimum SDK and target SDK attributes are defined\"\n" +
                "        explanation=\"The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for.)\"\n" +
                "        url=\"http://developer.android.com/guide/topics/manifest/uses-sdk-element.html\"\n" +
                "        errorLine1=\"    &lt;uses-sdk android:minSdkVersion=&quot;8&quot; />\"\n" +
                "        errorLine2=\"    ^\">\n" +
                "        <location\n" +
                "            file=\"/foo/Foo/AndroidManifest.xml\"\n" +
                "            line=\"7\"\n" +
                "            column=\"5\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"HardcodedText\"\n" +
                "        severity=\"Warning\"\n" +
                "        message=\"[I18N] Hardcoded string &quot;Fooo&quot;, should use @string resource\"\n" +
                "        category=\"Internationalization\"\n" +
                "        priority=\"5\"\n" +
                "        summary=\"Looks for hardcoded text attributes which should be converted to resource lookup\"\n" +
                "        explanation=\"Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
                "\n" +
                "* When creating configuration variations (for example for landscape or portrait)you have to repeat the actual text (and keep it up to date when making changes)\n" +
                "\n" +
                "* The application cannot be translated to other languages by just adding new translations for existing string resources.\n" +
                "\n" +
                "In Eclipse there is a quickfix to automatically extract this hardcoded string into a resource lookup.\"\n" +
                "        errorLine1=\" (java.lang.String)         android:text=&quot;Fooo&quot; />\"\n" +
                "        errorLine2=\"        ~~~~~~~~~~~~~~~~~~~\">\n" +
                "        <location\n" +
                "            file=\"/foo/bar/Foo/res/layout/main.xml\"\n" +
                "            line=\"12\"\n" +
                "            column=\"9\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "</issues>\n",
                report);

            // Make sure the XML is valid
            Document document = new PositionXmlParser().parse(report);
            assertNotNull(document);
            assertEquals(2, document.getElementsByTagName("issue").getLength());
        } finally {
            file.delete();
        }
    }

    @Override
    protected Detector getDetector() {
        fail("Not used in this test");
        return null;
    }
}
