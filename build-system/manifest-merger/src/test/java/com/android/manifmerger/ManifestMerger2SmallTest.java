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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.mock.MockLog;

import junit.framework.TestCase;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link com.android.manifmerger.ManifestMergerTest} class
 */
public class ManifestMerger2SmallTest extends TestCase {

    PlaceholderHandler.KeyBasedValueResolver<ManifestMerger2.SystemProperty> nullSystemResolver =
            new PlaceholderHandler.KeyBasedValueResolver<ManifestMerger2.SystemProperty>() {
                @Nullable
                @Override
                public String getValue(@NonNull ManifestMerger2.SystemProperty key) {
                    return null;
                }
            };

    PlaceholderHandler.KeyBasedValueResolver<ManifestMerger2.SystemProperty> keyBasedValueResolver =
            new PlaceholderHandler.KeyBasedValueResolver<ManifestMerger2.SystemProperty>() {
                @Nullable
                @Override
                public String getValue(@NonNull ManifestMerger2.SystemProperty key) {
                    if (key == ManifestMerger2.SystemProperty.PACKAGE) {
                        return "com.bar.new";
                    }
                    return null;
                }
            };

    public void testValidationFailure()
            throws ParserConfigurationException, SAXException, IOException,
            ManifestMerger2.MergeFailureException {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" "
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = inputAsFile("ManifestMerger2Test_testValidationFailure", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newInvoker(tmpFile, mockLog).merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            // check the log complains about the incorrect "tools:replace"
            assertStringPresenceInLogRecords(mergingReport, "tools:replace");
            assertFalse(mergingReport.getMergedDocument().isPresent());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    public void testPackageOverride()
            throws ParserConfigurationException, SAXException, IOException {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + "    package=\"com.foo.old\" >\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testPackageOverride#xml"), xml);

        ManifestMerger2.SystemProperty.PACKAGE.addTo(refDocument.getXml(), "com.bar.new");
        // verify the package value was overriden.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    public void testMissingPackageOverride()
            throws ParserConfigurationException, SAXException, IOException {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testMissingPackageOverride#xml"), xml);

        ManifestMerger2.SystemProperty.PACKAGE.addTo(refDocument.getXml(), "com.bar.new");
        // verify the package value was added.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    public void testAddingSystemProperties()
            throws ParserConfigurationException, SAXException, IOException {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        "testAddingSystemProperties#xml"), xml);

        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(document.getXml(), "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestMerger2.SystemProperty.VERSION_NAME.addTo(document.getXml(), "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestMerger2.SystemProperty.MIN_SDK_VERSION.addTo(document.getXml(), "10");
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestMerger2.SystemProperty.TARGET_SDK_VERSION.addTo(document.getXml(), "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));
    }

    public void testAddingSystemProperties_withDifferentPrefix()
            throws ParserConfigurationException, SAXException, IOException {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        "testAddingSystemProperties#xml"), xml
        );

        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(document.getXml(), "101");
        // using the non namespace aware API to make sure the prefix is the expected one.
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("t:versionCode"));
    }

    public void testOverridingSystemProperties()
            throws ParserConfigurationException, SAXException, IOException {
        String xml = ""
                + "<manifest versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <uses-sdk minSdkVersion=\"9\" targetSdkVersion=\".9\"/>\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        "testAddingSystemProperties#xml"), xml);
        // check initial state.
        assertEquals("34", document.getXml().getDocumentElement().getAttribute("versionCode"));
        assertEquals("3.4", document.getXml().getDocumentElement().getAttribute("versionName"));
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("9", usesSdk.getAttribute("minSdkVersion"));
        assertEquals(".9", usesSdk.getAttribute("targetSdkVersion"));


        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(document.getXml(), "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestMerger2.SystemProperty.VERSION_NAME.addTo(document.getXml(), "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestMerger2.SystemProperty.MIN_SDK_VERSION.addTo(document.getXml(), "10");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestMerger2.SystemProperty.TARGET_SDK_VERSION.addTo(document.getXml(), "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));
    }

    /**
     * Utility method to save a {@link String} XML into a file.
     */
    private File inputAsFile(String testName, String input) throws IOException {
        File tmpFile = File.createTempFile(testName, ".xml");
        FileWriter fw = null;
        try {
            fw = new FileWriter(tmpFile);
            fw.append(input);
        } finally {
            if (fw != null) fw.close();
        }
        return tmpFile;
    }

    private void assertStringPresenceInLogRecords(MergingReport mergingReport, String s) {
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            if (record.toString().contains(s)) {
                return;
            }
        }
        // failed, dump the records
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            Logger.getAnonymousLogger().info(record.toString());
        }
        fail("could not find " + s + " in logging records");
    }
}
