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
import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for the {@link ActionRecorder} class
 */
public class ActionRecorderTest extends TestCase {

    private static final String REFERENCE = ""
            + "<manifest\n"
            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
            + "    package=\"com.example.lib3\">\n"
            + "\n"
            + "    <activity android:name=\"activityOne\">\n"
            + "       <intent-filter android:label=\"@string/foo\"/>\n"
            + "    </activity>\n"
            + "\n"
            + "</manifest>";


    // this will be used as the source location for the "reference" xml string.
    private static final String REFEFENCE_DOCUMENT = "ref_doc";

    @Mock ILogger mLoggerMock;

    ActionRecorder.Builder mActionRecorderBuilder = new ActionRecorder.Builder();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testDoNothing() {
        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        actionRecorder.log(mLoggerMock);
        Mockito.verify(mLoggerMock).info(ActionRecorder.HEADER);
        Mockito.verifyNoMoreInteractions(mLoggerMock);
        assertTrue(actionRecorder.getAllRecords().isEmpty());
    }

    public void testSingleElement_withoutAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, ActionRecorder.ActionType.ADDED);

        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        ImmutableMap<String,ActionRecorder.DecisionTreeRecord> allRecords =
                actionRecorder.getAllRecords();
        assertEquals(1, allRecords.size());
        assertEquals(1, allRecords.get(xmlElement.getId()).getNodeRecords().size());
        assertEquals(0, allRecords.get(xmlElement.getId()).getAttributesRecords().size());
        actionRecorder.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ActionRecorder.HEADER)
            .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, REFEFENCE_DOCUMENT, 6);

        Mockito.verify(mLoggerMock).info(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withoutAttributes_withRejection()
            throws ParserConfigurationException, SAXException, IOException {

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

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        REFEFENCE_DOCUMENT), REFERENCE);

        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        "other_document"), other);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during initial document loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, ActionRecorder.ActionType.ADDED);
        // rejected during second document merging.
        mActionRecorderBuilder.recordNodeAction(xmlElement, ActionRecorder.ActionType.REJECTED,
                otherDocument.getRootNode().getNodeByTypeAndKey(
                        ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get());

        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        ImmutableMap<String,ActionRecorder.DecisionTreeRecord> allRecords =
                actionRecorder.getAllRecords();
        assertEquals(1, allRecords.size());
        assertEquals(2, allRecords.get(xmlElement.getId()).getNodeRecords().size());
        assertEquals(ActionRecorder.ActionType.ADDED,
                allRecords.get(xmlElement.getId()).getNodeRecords().get(0).mActionType);
        assertEquals(ActionRecorder.ActionType.REJECTED,
                allRecords.get(xmlElement.getId()).getNodeRecords().get(1).mActionType);
        assertEquals(0, allRecords.get(xmlElement.getId()).getAttributesRecords().size());
        actionRecorder.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ActionRecorder.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, REFEFENCE_DOCUMENT, 6);
        appendNode(stringBuilder, ActionRecorder.ActionType.REJECTED, "other_document", 6);

        Mockito.verify(mLoggerMock).info(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withNoNamespaceAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, ActionRecorder.ActionType.ADDED);
        mActionRecorderBuilder.recordAttributeAction(
                xmlElement.getAttribute(XmlNode.fromXmlName("android:name")).get(),
                ActionRecorder.ActionType.ADDED, AttributeOperationType.STRICT);

        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        ImmutableMap<String,ActionRecorder.DecisionTreeRecord> allRecords =
                actionRecorder.getAllRecords();
        assertEquals(1, allRecords.size());
        assertEquals(1, allRecords.get(xmlElement.getId()).getNodeRecords().size());
        assertEquals(1, allRecords.get(xmlElement.getId()).getAttributesRecords().size());
        actionRecorder.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ActionRecorder.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, REFEFENCE_DOCUMENT, 6);
        appendAttribute(stringBuilder,
                XmlNode.unwrapName(xmlElement.getXml().getAttributeNode("android:name")),
                ActionRecorder.ActionType.ADDED,
                REFEFENCE_DOCUMENT,
                6);

        Mockito.verify(mLoggerMock).info(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withNamespaceAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, ActionRecorder.ActionType.ADDED);
        mActionRecorderBuilder.recordAttributeAction(
                xmlElement.getAttribute(XmlNode.fromXmlName("package")).get(),
                ActionRecorder.ActionType.ADDED, AttributeOperationType.STRICT);

        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        ImmutableMap<String,ActionRecorder.DecisionTreeRecord> allRecords =
                actionRecorder.getAllRecords();
        assertEquals(1, allRecords.size());
        assertEquals(1, allRecords.get(xmlElement.getId()).getNodeRecords().size());
        assertEquals(ActionRecorder.ActionTarget.NODE,
                allRecords.get(xmlElement.getId()).getNodeRecords().get(0).getActionTarget());
        assertEquals(1, allRecords.get(xmlElement.getId()).getAttributesRecords().size());
        assertEquals(ActionRecorder.ActionTarget.ATTRIBUTE,
                allRecords.get(xmlElement.getId()).getAttributesRecords()
                        .get(XmlNode.fromXmlName("package")).get(0).getActionTarget());
        actionRecorder.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ActionRecorder.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, REFEFENCE_DOCUMENT, 1);
        appendAttribute(stringBuilder,
                XmlNode.unwrapName(xmlElement.getXml().getAttributeNode("package")),
                ActionRecorder.ActionType.ADDED,
                REFEFENCE_DOCUMENT,
                4);

        Mockito.verify(mLoggerMock).info(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testMultipleElements_withRejection()
            throws ParserConfigurationException, SAXException, IOException {

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\""
                + "         android:configChanges=\"locale\"/>\n"
                + "    <application android:name=\"applicationOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        REFEFENCE_DOCUMENT), REFERENCE);

        XmlDocument otherDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),
                        "other_document"), other);

        XmlElement activityElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during initial document loading
        mActionRecorderBuilder.recordNodeAction(activityElement, ActionRecorder.ActionType.ADDED);
        // rejected during second document merging.
        mActionRecorderBuilder.recordNodeAction(activityElement, ActionRecorder.ActionType.REJECTED,
                otherDocument.getRootNode().getNodeByTypeAndKey(
                        ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get());
        XmlElement applicationElement = otherDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.APPLICATION, null).get();
        mActionRecorderBuilder.recordNodeAction(applicationElement, ActionRecorder.ActionType.ADDED);

        ActionRecorder actionRecorder = mActionRecorderBuilder.build();
        ImmutableMap<String,ActionRecorder.DecisionTreeRecord> allRecords =
                actionRecorder.getAllRecords();
        assertEquals(2, allRecords.size());
        assertEquals(2, allRecords.get(activityElement.getId()).getNodeRecords().size());
        assertEquals(ActionRecorder.ActionType.ADDED,
                allRecords.get(activityElement.getId()).getNodeRecords().get(0).mActionType);
        assertEquals(ActionRecorder.ActionType.REJECTED,
                allRecords.get(activityElement.getId()).getNodeRecords().get(1).mActionType);
        assertEquals(0, allRecords.get(activityElement.getId()).getAttributesRecords().size());
        assertEquals(1, allRecords.get(applicationElement.getId()).getNodeRecords().size());
        assertEquals(0, allRecords.get(applicationElement.getId()).getAttributesRecords().size());
        actionRecorder.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(ActionRecorder.HEADER)
                .append(activityElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, REFEFENCE_DOCUMENT, 6);
        appendNode(stringBuilder, ActionRecorder.ActionType.REJECTED, "other_document", 6);
        stringBuilder.append(applicationElement.getId()).append("\n");
        appendNode(stringBuilder, ActionRecorder.ActionType.ADDED, "other_document", 7);

        Mockito.verify(mLoggerMock).info(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }


    private void appendNode(StringBuilder out,
            ActionRecorder.ActionType actionType,
            String docString,
            int lineNumber) {

        out.append(actionType.toString())
                .append(" from ")
                .append(getClass().getSimpleName()).append('#').append(docString)
                .append(":").append(lineNumber).append("\n");
    }

    private void appendAttribute(StringBuilder out,
            XmlNode.NodeName attributeName,
            ActionRecorder.ActionType actionType,
            String docString,
            int lineNumber) {

        out.append("\t")
                .append(attributeName.toString())
                .append("\t\t")
                .append(actionType.toString())
                .append(" from ")
                .append(getClass().getSimpleName()).append('#').append(docString)
                .append(":").append(lineNumber).append("\n");
    }
}
