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
import com.android.utils.PositionXmlParser;
import com.google.common.base.Optional;

import junit.framework.TestCase;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for {@link XmlLoader}
 */
public class XmlLoaderTest extends TestCase {

    public void testAndroidPrefix() throws IOException, SAXException, ParserConfigurationException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),"testToolsPrefix()"), input);
        Optional<XmlElement> applicationOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        assertTrue(applicationOptional.isPresent());
        Node label = applicationOptional.get().getXml().getAttributes().item(0);
        assertEquals("label", label.getLocalName());
        assertEquals(SdkConstants.ANDROID_URI, label.getNamespaceURI());
        assertEquals("android:label", label.getNodeName());
    }

    public void testToolsPrefix() throws IOException, SAXException, ParserConfigurationException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" tools:node=\"replace\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(),"testToolsPrefix()"),input);
        Optional<XmlElement> applicationOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        assertTrue(applicationOptional.isPresent());
        Element application = applicationOptional.get().getXml();
        assertEquals(2, application.getAttributes().getLength());
        Attr label = application.getAttributeNodeNS(SdkConstants.ANDROID_URI, "label");
        assertEquals("android:label", label.getNodeName());
        Attr tools = application.getAttributeNodeNS(SdkConstants.TOOLS_URI,
                NodeOperationType.NODE_LOCAL_NAME);
        assertEquals("replace", tools.getNodeValue());

        // check positions.
        PositionXmlParser.Position applicationPosition = applicationOptional.get().getPosition();
        assertNotNull(applicationPosition);
        assertEquals(6, applicationPosition.getLine());
        assertEquals(5, applicationPosition.getColumn());

        XmlAttribute xmlAttribute =
                new XmlAttribute(applicationOptional.get(), tools, null /* AttributeModel */);
        PositionXmlParser.Position toolsPosition = xmlAttribute.getPosition();
        assertNotNull(toolsPosition);
        assertEquals(6, toolsPosition.getLine());
        assertEquals(51, toolsPosition.getColumn());
    }

    public void testUnusualPrefixes()
            throws IOException, SAXException, ParserConfigurationException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:x=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:y=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application x:label=\"@string/lib_name\" y:node=\"replace\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testUnusualPrefixes()"), input);
        Optional<XmlElement> applicationOptional = xmlDocument.getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        assertTrue(applicationOptional.isPresent());
        Element application = applicationOptional.get().getXml();
        assertEquals(2, application.getAttributes().getLength());
        Node label = application.getAttributeNodeNS(SdkConstants.ANDROID_URI, "label");
        assertEquals("x:label", label.getNodeName());
        Node tools = application.getAttributeNodeNS(SdkConstants.TOOLS_URI,
                NodeOperationType.NODE_LOCAL_NAME);
        assertEquals("y:node", tools.getNodeName());
        assertEquals("replace", tools.getNodeValue());
    }



}
