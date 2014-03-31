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

import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for {@link com.android.manifmerger.XmlDocument}
 */
public class XmlDocumentTest extends TestCase {

    @Mock ILogger mLogger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testMergeableElementsIdentification()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMergeableElementsIdentification()"), input);
        ImmutableList<XmlElement> mergeableElements = xmlDocument.getRootNode().getMergeableElements();
        assertEquals(2, mergeableElements.size());
    }

    public void testGetXmlNodeByTypeAndKey()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testGetXmlNodeByTypeAndKey()"), input);
        assertTrue(xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").isPresent());
        assertFalse(xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "noName").isPresent());
    }

    public void testSimpleMerge()
            throws ParserConfigurationException, SAXException, IOException {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testSimpleMerge()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testSimpleMerge()"), library);
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger);
        Optional<XmlDocument> mergedDocument =
                mainDocument.merge(libraryDocument, mergingReportBuilder);

        assertTrue(mergedDocument.isPresent());
        Logger.getAnonymousLogger().info(mergedDocument.get().prettyPrint());
        assertTrue(mergedDocument.get().getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.APPLICATION, null).isPresent());
        Optional<XmlElement> activityOne = mergedDocument.get()
                .getRootNode().getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityOne");
        assertTrue(activityOne.isPresent());

    }

    public void testDiff1()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), library);
        assertTrue(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testDiff2()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), library);
        assertFalse(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testDiff3()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "    <!-- some comment that should be ignored -->\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <!-- some comment that should also be ignored -->\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), library);
        assertFalse(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testWriting() throws ParserConfigurationException, SAXException, IOException {
        String input = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:x=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:y=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\" >\n"
                + "\n"
                + "    <application\n"
                + "        x:label=\"@string/lib_name\"\n"
                + "        y:node=\"replace\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testWriting()"), input);
        assertEquals(input, xmlDocument.prettyPrint());
    }
}
