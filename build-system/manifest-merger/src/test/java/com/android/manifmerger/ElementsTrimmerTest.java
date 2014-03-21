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

import com.android.SdkConstants;
import com.android.utils.ILogger;
import com.android.xml.AndroidManifest;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link ElementsTrimmer} class
 */
public class ElementsTrimmerTest extends TestCase {

    @Mock
    ILogger mILogger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testNoUseFeaturesDeclaration()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <permission android:name=\"permissionOne\" "
                + "         permissionGroup=\"permissionGroupOne\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testNoUseFeaturesDeclaration"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);
        assertEquals(0, mergingReport.getActionRecorder().build().getAllRecords().size());
    }


    public void testNothingToTrim()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x0002000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testNothingToTrim"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);
        assertEquals(0, mergingReport.getActionRecorder().build().getAllRecords().size());
    }


    public void testMultipleAboveTwoResults()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature"
                + "             android:required=\"true\""
                + "             android:glEsVersion=\"0x00020000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00021000\"/>\n"
                + "    <uses-feature"
                + "             android:glEsVersion=\"0x00022000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00030000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMultipleAboveTwoResults"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);

        // check action recording.
        checkActionsRecording(mergingReport, 2);

        // check results.
        checkResults(xmlDocument, ImmutableList.of("0x00030000", "0x00022000"));
    }

    public void testSingleAboveTwoResults()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature android:glEsVersion=\"0x00020000\"/>\n"
                + "    <uses-feature android:glEsVersion=\"0x00021000\"/>\n"
                + "    <uses-feature android:glEsVersion=\"0x00030000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testSingleAboveTwoResults"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);

        // check action recording.
        checkActionsRecording(mergingReport, 2);

        // check results.
        checkResults(xmlDocument, ImmutableList.of("0x00030000"));
    }

    public void testMultipleBelowTwoResults()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature"
                + "             android:required=\"true\""
                + "             android:glEsVersion=\"0x00010000\"/>\n"
                + "    <uses-feature"
                + "             android:glEsVersion=\"0x00011000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00012000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMultipleBelowTwoResults"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);

        // check action recording.
        checkActionsRecording(mergingReport, 1);

        // check results.
        checkResults(xmlDocument, ImmutableList.of("0x00011000", "0x00012000"));
    }

    public void testSingleBelowTwoResults()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature android:glEsVersion=\"0x00010000\"/>\n"
                + "    <uses-feature android:glEsVersion=\"0x00011000\"/>\n"
                + "    <uses-feature android:glEsVersion=\"0x00012000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testSingleBelowTwoResults"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);

        // check action recording.
        checkActionsRecording(mergingReport, 2);

        // check results.
        checkResults(xmlDocument, ImmutableList.of("0x00012000"));
    }

    public void testMultipleAboveAndBelowTwoResults()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature"
                + "             android:required=\"true\""
                + "             android:glEsVersion=\"0x00010000\"/>\n"
                + "    <uses-feature"
                + "             android:glEsVersion=\"0x00011000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00012000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"true\""
                + "             android:glEsVersion=\"0x00020000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00021000\"/>\n"
                + "    <uses-feature"
                + "             android:glEsVersion=\"0x00022000\"/>\n"
                + "    <uses-feature"
                + "             android:required=\"false\""
                + "             android:glEsVersion=\"0x00030000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMultipleAboveAndBelowTwoResults"), input);

        MergingReport.Builder mergingReport = new MergingReport.Builder(mILogger);
        ElementsTrimmer.trim(xmlDocument, mergingReport);
        assertFalse(mergingReport.hasErrors());
        Mockito.verifyZeroInteractions(mILogger);

        // check action recording.
        checkActionsRecording(mergingReport, 3);

        // check results.
        checkResults(xmlDocument,
                ImmutableList.of("0x00011000", "0x00012000", "0x00022000", "0x00030000"));
    }

    private static void checkActionsRecording(
            MergingReport.Builder mergingReport,
            int expectedActionsNumber) {

        ActionRecorder actionRecorder = mergingReport.getActionRecorder().build();
        assertEquals(expectedActionsNumber, actionRecorder.getAllRecords().size());
        for (int i = 0; i < expectedActionsNumber; i++) {
            ActionRecorder.DecisionTreeRecord decisionTreeRecord = actionRecorder.getAllRecords()
                    .values().asList().get(i);
            assertEquals(1, decisionTreeRecord.getNodeRecords().size());
            assertEquals(ActionRecorder.ActionType.REJECTED,
                    decisionTreeRecord.getNodeRecords().get(0).getActionType());
        }
    }

    private static void checkResults(XmlDocument xmlDocument, List<String> expectedVersions) {
        NodeList elementsByTagName = xmlDocument.getRootNode().getXml()
                .getElementsByTagName("uses-feature");
        assertEquals(expectedVersions.size(), elementsByTagName.getLength());
        for (int i = 0; i < elementsByTagName.getLength(); i++) {
            Element item = (Element) elementsByTagName.item(i);
            Attr glEsVersion = item.getAttributeNodeNS(SdkConstants.ANDROID_URI,
                    AndroidManifest.ATTRIBUTE_GLESVERSION);
            assertNotNull(glEsVersion);
            assertTrue(expectedVersions.contains(glEsVersion.getValue()));
        }
    }
}
