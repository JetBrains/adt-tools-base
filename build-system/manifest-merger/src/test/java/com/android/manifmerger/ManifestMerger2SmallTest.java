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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.manifmerger.MergingReport.MergedManifestKind;
import com.android.sdklib.mock.MockLog;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link ManifestMergerTestUtil} class
 */
@RunWith(MockitoJUnitRunner.class)
public class ManifestMerger2SmallTest {

    @Mock
    private ActionRecorder mActionRecorder;

    @Test
    public void testValidationFailure() throws Exception {

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
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION).merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            // check the log complains about the incorrect "tools:replace"
            assertStringPresenceInLogRecords(mergingReport, "tools:replace");
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemoval() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNull(replace);
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemovalForLibraries() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.app1\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "       <activity tools:node=\"removeAll\">\n"
                + "        </activity>\n"
                + "    </application>"
                + "\n"
                + "</manifest>";

        File overlayFile = inputAsFile("testToolsAnnotationRemoval", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput = ""
                + "<manifest\n"
                + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "package=\"com.example.app1\">\n"
                + "\n"
                + "<application android:name=\"TheApp\" >\n"
                + "    <!-- Activity to configure widget -->\n"
                + "    <activity\n"
                + "            android:icon=\"@drawable/widget_icon\"\n"
                + "            android:label=\"Configure Widget\"\n"
                + "            android:name=\"com.example.lib1.WidgetConfigurationUI\"\n"
                + "            android:theme=\"@style/Theme.WidgetConfigurationUI\" >\n"
                + "        <intent-filter >\n"
                + "            <action android:name=\"android.appwidget.action.APPWIDGET_CONFIGURE\" />\n"
                + "        </intent-filter>\n"
                + "    </activity>\n"
                + "</application>\n"
                + "\n"
                + "</manifest>";
        File libFile = inputAsFile("testToolsAnnotationRemoval", libraryInput);


        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            assertTrue(applications.getLength() == 0);
        } finally {
            assertTrue(overlayFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationPresence() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.LIBRARY)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNotNull(replace);
            assertEquals("tools:replace value not correct", "label", replace.getNodeValue());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }


    @Test
    public void testPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + "    package=\"com.foo.old\" >\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                TestUtils.sourceFile(getClass(), "testPackageOverride#xml"), xml);

        ManifestMerger2.SystemProperty.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was overridden.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testMissingPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                TestUtils.sourceFile(getClass(), "testMissingPackageOverride#xml"), xml);

        ManifestMerger2.SystemProperty.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was added.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testAddingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                TestUtils.sourceFile(getClass(),
                        "testAddingSystemProperties#xml"), xml);

        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestMerger2.SystemProperty.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestMerger2.SystemProperty.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestMerger2.SystemProperty.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));

        ManifestMerger2.SystemProperty.MAX_SDK_VERSION.addTo(mActionRecorder, document, "16");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("16", usesSdk.getAttribute("android:maxSdkVersion"));
    }

    @Test
    public void testAddingSystemProperties_withDifferentPrefix() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                TestUtils.sourceFile(getClass(),
                        "testAddingSystemProperties#xml"), xml
        );

        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        // using the non namespace aware API to make sure the prefix is the expected one.
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("t:versionCode"));
    }

    @Test
    public void testOverridingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <uses-sdk minSdkVersion=\"9\" targetSdkVersion=\".9\"/>\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document = TestUtils.xmlDocumentFromString(
                TestUtils.sourceFile(getClass(),
                        "testAddingSystemProperties#xml"), xml);
        // check initial state.
        assertEquals("34", document.getXml().getDocumentElement().getAttribute("versionCode"));
        assertEquals("3.4", document.getXml().getDocumentElement().getAttribute("versionName"));
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("9", usesSdk.getAttribute("minSdkVersion"));
        assertEquals(".9", usesSdk.getAttribute("targetSdkVersion"));


        ManifestMerger2.SystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestMerger2.SystemProperty.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestMerger2.SystemProperty.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestMerger2.SystemProperty.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));
    }

    @Test
    public void testPlaceholderSubstitution() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\".activityOne\" android:label=\"${labelName}\"/>\n"
                + "</manifest>";

        Map<String, String> placeholders = ImmutableMap.of("labelName", "injectedLabelName");
        MockLog mockLog = new MockLog();
        File inputFile = inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .setPlaceHolderValues(placeholders)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertNotNull(document);
            Optional<Element> activityOne = getElementByTypeAndKey(
                    document, "activity", "foo.activityOne");
            assertTrue(activityOne.isPresent());
            Attr label = activityOne.get().getAttributeNodeNS(SdkConstants.ANDROID_URI, "label");
            assertNotNull(label);
            assertEquals("injectedLabelName", label.getValue());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testApplicationIdSubstitution() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .setOverride(ManifestMerger2.SystemProperty.PACKAGE, "bar")
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals("bar", document.getElementsByTagName("manifest")
                    .item(0).getAttributes().getNamedItem("package").getNodeValue());
            Optional<Element> activityOne = getElementByTypeAndKey(document, "activity",
                    "bar.activityOne");
            assertTrue(activityOne.isPresent());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoApplicationIdValueProvided() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals("foo", document.getElementsByTagName("manifest")
                    .item(0).getAttributes().getNamedItem("package").getNodeValue());
            Optional<Element> activityOne = getElementByTypeAndKey(document, "activity",
                    "foo.activityOne");
            assertTrue(activityOne.isPresent());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoFqcnsExtraction() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.example\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("com.foo.example.activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals("com.foo.example.myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testFqcnsExtraction() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.example\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(".activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(".activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(".applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals(".myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testNoPlaceholderReplacement() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testReplaceInputStream() throws Exception {
        // This test is identical to testNoPlaceholderReplacement but instead
        // of reading from a string, we test the ManifestMerger's ability to
        // supply a custom input stream
        final String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";
        String staleContent = "<manifest />";

        // Note: disk content is wrong/stale; make sure we read the live content instead
        File inputFile = inputAsFile("testNoPlaceHolderReplacement", staleContent);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                .withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
                    @Override
                    protected InputStream getInputStream(@NonNull File file)
                            throws FileNotFoundException {
                        return new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8));
                    }
                })
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testInstantRunReplacement() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_RUN));
        assertEquals("com.foo.bar.applicationOne",
                xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0).getAttributes().getNamedItem(
                            SdkConstants.ATTR_NAME).getNodeValue());
        assertEquals(ManifestMerger2.BOOTSTRAP_APPLICATION,
                xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0).getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_NAME).getNodeValue());
    }

    @Test
    public void testInstantRunReplacementWithNoAppName() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "    <application android:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_RUN));
        assertEquals(ManifestMerger2.BOOTSTRAP_APPLICATION,
                xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0).getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME).getNodeValue());
    }

    public static Optional<Element> getElementByTypeAndKey(Document xmlDocument, String nodeType, String key) {
        NodeList elementsByTagName = xmlDocument.getElementsByTagName(nodeType);
        for (int i = 0; i < elementsByTagName.getLength(); i++) {
            Node item = elementsByTagName.item(i);
            Node name = item.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, "name");
            if ((name == null && key == null) || (name != null && key.equals(name.getNodeValue()))) {
                return Optional.of((Element) item);
            }
        }
        return Optional.absent();
    }

    /**
     * Utility method to save a {@link String} XML into a file.
     */
    private static File inputAsFile(String testName, String input) throws IOException {
        File tmpFile = File.createTempFile(testName, ".xml");
        tmpFile.deleteOnExit();
        Files.write(input, tmpFile, Charsets.UTF_8);
        return tmpFile;
    }

    private static void assertStringPresenceInLogRecords(MergingReport mergingReport, String s) {
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

    private static Document parse(String xml)
            throws IOException, SAXException, ParserConfigurationException {
        return XmlUtils.parseDocument(xml, true /* namespaceAware */);
    }
}
