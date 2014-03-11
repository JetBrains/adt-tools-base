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

import com.android.utils.StdLogger;
import com.google.common.base.Optional;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link XmlElement}
 */
public class XmlElementTest extends TestCase {

    @Mock
    MergingReport.Builder mergingReport;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testToolsNodeInstructions()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         tools:node=\"remove\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityTwo\" "
                + "         tools:node=\"removeAll\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityThree\" "
                + "         tools:node=\"removeChildren\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testToolsNodeInstructions()"), input);
        Optional<XmlElement> activity = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne");
        assertTrue(activity.isPresent());
        assertEquals(NodeOperationType.REMOVE,
                activity.get().getOperationType());
        activity = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityTwo");
        assertTrue(activity.isPresent());
        assertEquals(NodeOperationType.REMOVE_ALL,
                activity.get().getOperationType());
        activity = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityThree");
        assertTrue(activity.isPresent());
        assertEquals(NodeOperationType.REMOVE_CHILDREN,
                activity.get().getOperationType());
    }

    public void testInvalidNodeInstruction()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         tools:node=\"funkyValue\"/>\n"
                + "\n"
                + "</manifest>";

        try {
            XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                    new TestUtils.TestSourceLocation(
                            getClass(), "testInvalidNodeInstruction()"), input);
            xmlDocument.getRootNode();
            fail("Exception not thrown");
        } catch (IllegalArgumentException expected) {
            // expected.
        }
    }

    public void testAttributeInstructions()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         tools:remove=\"android:theme\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityTwo\" "
                + "         android:theme=\"@theme1\"\n"
                + "         tools:replace=\"android:theme\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityThree\" "
                + "         tools:strict=\"android:exported\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityFour\" "
                + "         android:theme=\"@theme1\"\n"
                + "         android:exported=\"true\"\n"
                + "         android:windowSoftInputMode=\"stateUnchanged\"\n"
                + "         tools:replace="
                + "\"android:theme, android:exported,android:windowSoftInputMode\"/>\n"
                + "\n"
                + "    <activity android:name=\"activityFive\" "
                + "         android:theme=\"@theme1\"\n"
                + "         android:exported=\"true\"\n"
                + "         android:windowSoftInputMode=\"stateUnchanged\"\n"
                + "         tools:remove=\"android:exported\"\n"
                + "         tools:replace=\"android:theme\"\n"
                + "         tools:strict=\"android:windowSoftInputMode\"/>\n"
                + "\n"
                + "</manifest>";

        // ActivityOne, remove operation.
        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testAttributeInstructions()"), input);
        Optional<XmlElement> activityOptional = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne");
        assertTrue(activityOptional.isPresent());
        XmlElement activity = activityOptional.get();
        assertEquals(1, activity.getAttributeOperations().size());
        AttributeOperationType attributeOperationType =
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme"));
        assertEquals(AttributeOperationType.REMOVE, attributeOperationType);

        // ActivityTwo, replace operation.
        activityOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityTwo");
        assertTrue(activityOptional.isPresent());
        activity = activityOptional.get();
        assertEquals(1, activity.getAttributeOperations().size());
        attributeOperationType = activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme"));
        assertEquals(AttributeOperationType.REPLACE, attributeOperationType);

        // ActivityThree, strict operation.
        activityOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityThree");
        assertTrue(activityOptional.isPresent());
        activity = activityOptional.get();
        assertEquals(1, activity.getAttributeOperations().size());
        attributeOperationType = activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme"));
        assertEquals(AttributeOperationType.STRICT, attributeOperationType);

        // ActivityFour, multiple target fields.
        activityOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityFour");
        assertTrue(activityOptional.isPresent());
        activity = activityOptional.get();
        assertEquals(3, activity.getAttributeOperations().size());
        assertEquals(AttributeOperationType.REPLACE,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme")));
        assertEquals(AttributeOperationType.REPLACE,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme")));
        assertEquals(AttributeOperationType.REPLACE,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme")));

        // ActivityFive, multiple operations.
        activityOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityFive");
        assertTrue(activityOptional.isPresent());
        activity = activityOptional.get();
        assertEquals(3, activity.getAttributeOperations().size());

        assertEquals(AttributeOperationType.REMOVE,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:exported")));

        assertEquals(AttributeOperationType.REPLACE,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme")));

        assertEquals(AttributeOperationType.STRICT,
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:windowSoftInputMode")));
    }

    public void testNoNamespaceAwareAttributeInstructions()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         tools:remove=\"theme\"/>\n"
                + "\n"
                + "</manifest>";

        // ActivityOne, remove operation.
        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testAttributeInstructions()"), input);
        Optional<XmlElement> activityOptional = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne");
        assertTrue(activityOptional.isPresent());
        XmlElement activity = activityOptional.get();
        assertEquals(1, activity.getAttributeOperations().size());
        AttributeOperationType attributeOperationType =
                activity.getAttributeOperationType(XmlNode.fromXmlName("android:theme"));
        assertEquals(AttributeOperationType.REMOVE, attributeOperationType);
    }

    public void testUnusualNamespacePrefixAttributeInstructions()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:z=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity z:name=\"activityOne\" tools:remove=\"theme\"/>\n"
                + "\n"
                + "</manifest>";

        // ActivityOne, remove operation.
        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testAttributeInstructions()"), input);
        Optional<XmlElement> activityOptional = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne");
        assertTrue(activityOptional.isPresent());
        XmlElement activity = activityOptional.get();

        assertEquals(1, activity.getAttributeOperations().size());
        AttributeOperationType attributeOperationType =
                activity.getAttributeOperationType(XmlNode.fromXmlName("z:theme"));
        assertEquals(AttributeOperationType.REMOVE, attributeOperationType);
    }

    public void testInvalidAttributeInstruction()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         tools:bad-name=\"android:theme\"/>\n"
                + "\n"
                + "</manifest>";

        try {
            XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                    new TestUtils.TestSourceLocation(getClass(), "testDiff6()"), input);
            xmlDocument.getRootNode();
            fail("Exception not thrown");
        } catch (IllegalArgumentException expected) {
            // expected.
        }
    }

    public void testDiff1()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";
        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), other);

        assertTrue(refDocument.getRootNode().getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                "com.example.lib3.activityOne").get()
                .compareTo(
                        otherDocument.getRootNode().getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                                .get(),
                        mergingReport));
    }

    public void testDiff2()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"mcc\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), other);

        assertFalse(refDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get()
                .compareTo(
                        otherDocument.getRootNode().getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                                .get(),
                        mergingReport));
    }

    public void testDiff3()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\" android:exported=\"true\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), other);

        assertFalse(refDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get()
                .compareTo(
                        otherDocument.getRootNode().getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                                .get(),
                        mergingReport));
    }

    public void testDiff4()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\" android:exported=\"false\"/>\n"
                + "\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff4()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff4()"), other);
        assertFalse(refDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get()
                .compareTo(
                        otherDocument.getRootNode().getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                                .get(),
                        mergingReport));
    }

    public void testDiff5()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\">\n"
                + "\n"
                + "    </activity>\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff5()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff5()"), other);

        assertTrue(refDocument.getRootNode().compareTo(otherDocument.getRootNode(),
                mergingReport));
    }

    public void testDiff6()
            throws Exception {

        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\">\n"
                + "\n"
                + "       <intent-filter android:label=\"@string/foo\"/>\n"
                + "\n"
                + "    </activity>\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff6()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff6()"), other);
        assertFalse(
                refDocument.getRootNode().getNodeByTypeAndKey(
                        ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get()
                        .compareTo(
                                otherDocument.getRootNode().getNodeByTypeAndKey(
                                        ManifestModel.NodeTypes.ACTIVITY,
                                        "com.example.lib3.activityOne")
                                        .get(),
                                mergingReport));
    }

    /**
     * test merging of same element types with no collision.
     */
    public void testMerge_NoCollision() throws ParserConfigurationException, SAXException, IOException {
        String reference = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\">\n"
                + "       <intent-filter android:label=\"@string/foo\"/>\n"
                + "    </activity>\n"
                + "\n"
                + "</manifest>";

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityTwo\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testMerge()"), reference);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testMerge()"), other);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(
                new StdLogger(StdLogger.Level.VERBOSE));
        Optional<XmlDocument> result = refDocument.merge(otherDocument, mergingReportBuilder);
        assertTrue(result.isPresent());
        XmlDocument resultDocument = result.get();

        Optional<XmlElement> activityOne = resultDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityOne");
        assertTrue(activityOne.isPresent());

        Optional<XmlElement> activityTwo = resultDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityTwo");
        assertTrue(activityTwo.isPresent());
    }

    /**
     * test merging of same element with no attribute collision.
     */
    public void testAttributeMerging()
            throws ParserConfigurationException, SAXException, IOException {
        String higherPriority = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\">\n"
                + "       <intent-filter android:label=\"@string/foo\"/>\n"
                + "    </activity>\n"
                + "\n"
                + "</manifest>";

        String lowerPriority = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" \n"
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument refDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "higherPriority"), higherPriority);
        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "lowerPriority"), lowerPriority);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(
                new StdLogger(StdLogger.Level.VERBOSE));
        Optional<XmlDocument> result = refDocument.merge(otherDocument, mergingReportBuilder);
        assertTrue(result.isPresent());
        XmlDocument resultDocument = result.get();

        Optional<XmlElement> activityOne = resultDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityOne");
        assertTrue(activityOne.isPresent());

        // verify that both attributes are in the resulting merged element.
        List<XmlAttribute> attributes = activityOne.get().getAttributes();
        assertEquals(2, attributes.size());
        assertTrue(activityOne.get().getAttribute(
                XmlNode.fromXmlName("android:configChanges")).isPresent());
        assertTrue(activityOne.get().getAttribute(
                XmlNode.fromXmlName("android:name")).isPresent());
    }
}
