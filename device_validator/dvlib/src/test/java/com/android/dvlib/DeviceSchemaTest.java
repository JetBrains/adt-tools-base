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

package com.android.dvlib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class DeviceSchemaTest extends TestCase {

    private void checkFailure(Map<String, String> replacements, String regex) throws Exception {
        // Generate XML stream with replacements
        InputStream xmlStream = getReplacedStream(replacements);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertFalse(
                "Validation Assertion Failed, XML failed to validate when it was expected to pass\n",
                DeviceSchema.validate(xmlStream, baos, null));
        assertTrue(String.format("Regex Assertion Failed:\nExpected: %s\nActual: %s\n", regex, baos
                .toString().trim()), baos.toString().trim().matches(regex));
    }

    private void checkFailure(String resource, String regex) throws Exception {
        InputStream xml = DeviceSchemaTest.class.getResourceAsStream(resource);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertFalse("Validation Assertion Failed, XML validated when it was expected to fail\n",
                DeviceSchema.validate(xml, baos, null));
        assertTrue(String.format("Regex Assertion Failed:\nExpected: %s\nActual: %s\n", regex, baos
                .toString().trim()), baos.toString().trim().matches(regex));
    }

    private void checkSuccess(Map<String, String> replacements) throws Exception {
        InputStream xmlStream = getReplacedStream(replacements);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(DeviceSchema.validate(xmlStream, baos, null));
        assertTrue(baos.toString().trim().matches(""));
    }

    public static InputStream getReplacedStream(Map<String, String> replacements) throws Exception {
        InputStream xml = DeviceSchema.class.getResourceAsStream("devices_minimal.xml");
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();
        ReplacementHandler replacer = new ReplacementHandler(replacements);
        parser.parse(xml, replacer);
        Document doc = replacer.getGeneratedDocument();
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        // Add indents so we're closer to user generated output
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StringWriter out = new StringWriter();
        StreamResult result = new StreamResult(out);
        tf.transform(source, result);
        return new ByteArrayInputStream(out.toString().getBytes("UTF-8"));
    }

    public void testValidXml() throws Exception {
        InputStream xml = DeviceSchemaTest.class.getResourceAsStream("devices.xml");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean result = DeviceSchema.validate(xml, baos, null);
        String output = baos.toString().trim();
        assertTrue(
                String.format(
                        "Validation Assertion Failed, XML failed to validate when it was expected to pass\n%s\n",output), result);
        assertTrue(String.format("Regex Assertion Failed\nExpected No Output\nActual: %s\n", baos
                .toString().trim()), baos.toString().trim().matches(""));
    }

    public void testNoHardware() throws Exception {
        String regex = "Error: cvc-complex-type.2.4.a: Invalid content was found starting with "
                + "element 'd:software'.*";
        checkFailure("devices_no_hardware.xml", regex);
    }

    public void testNoSoftware() throws Exception {
        String regex = "Error: cvc-complex-type.2.4.a: Invalid content was found starting with "
                + "element 'd:state'.*";
        checkFailure("devices_no_software.xml", regex);
    }

    public void testNoDefault() throws Exception {
        String regex = "Error: No default state for device Galaxy Nexus.*";
        checkFailure("devices_no_default.xml", regex);
    }

    public void testTooManyDefaults() throws Exception {
        String regex = "Error: More than one default state for device Galaxy Nexus.*";
        checkFailure("devices_too_many_defaults.xml", regex);
    }

    public void testNoStates() throws Exception {
        String regex = "Error: cvc-complex-type.2.4.b: The content of element 'd:device' is not "
                + "complete.*\nError: No default state for device Galaxy Nexus.*";
        checkFailure("devices_no_states.xml", regex);
    }

    public void testBadMechanism() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_MECHANISM, "fanger");
        checkFailure(replacements, "Error: cvc-enumeration-valid: Value 'fanger' is not "
                + "facet-valid.*\nError: cvc-type.3.1.3: The value 'fanger' of element "
                + "'d:mechanism' is not valid.*");
    }

    public void testNegativeXdpi() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_XDPI, "-1.0");
        checkFailure(replacements, "Error: cvc-minInclusive-valid: Value '-1.0'.*\n"
                + "Error: cvc-type.3.1.3: The value '-1.0' of element 'd:xdpi' is not valid.*");
    }

    public void testNegativeYdpi() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_YDPI, "-1");
        checkFailure(replacements, "Error: cvc-minInclusive-valid: Value '-1'.*\n"
                + "Error: cvc-type.3.1.3: The value '-1' of element 'd:ydpi' is not valid.*");

    }

    public void testNegativeDiagonalLength() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_DIAGONAL_LENGTH, "-1.0");

        checkFailure(replacements, "Error: cvc-minInclusive-valid: Value '-1.0'.*\n"
                + "Error: cvc-type.3.1.3: The value '-1.0' of element 'd:diagonal-length'.*");

    }

    public void testInvalidOpenGLVersion() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_GL_VERSION, "2");
        checkFailure(replacements, "Error: cvc-pattern-valid: Value '2' is not facet-valid.*\n"
                + "Error: cvc-type.3.1.3: The value '2' of element 'd:gl-version' is not valid.*");
    }

    public void testEmptyOpenGLExtensions() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_GL_EXTENSIONS, "");
        checkSuccess(replacements);
    }

    public void testEmptySensors() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_SENSORS, "");
        checkSuccess(replacements);
    }

    public void testEmptyNetworking() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_NETWORKING, "");
        checkSuccess(replacements);
    }

    public void testEmptyCpu() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_CPU, "");
        checkFailure(replacements, "Error: cvc-minLength-valid: Value '' with length = '0'.*\n"
                + "Error: cvc-type.3.1.3: The value '' of element 'd:cpu' is not valid.*");
    }

    public void testEmptyGpu() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put(DeviceSchema.NODE_GPU, "");
        checkFailure(replacements, "Error: cvc-minLength-valid: Value '' with length = '0'.*\n"
                + "Error: cvc-type.3.1.3: The value '' of element 'd:gpu' is not valid.*");
    }

    /**
     * Reads in a valid devices XML file and if an element tag is in the
     * replacements map, it replaces its text content with the corresponding
     * value. Note this has no concept of namespaces or hierarchy, so it will
     * replace the contents any and all elements with the specified tag name.
     */
    private static class ReplacementHandler extends DefaultHandler {
        private Element mCurrElement = null;
        private Document mDocument;
        private final Stack<Element> mElementStack = new Stack<Element>();
        private final Map<String, String> mPrefixes = new HashMap<String, String>();
        private final Map<String, String> mReplacements;
        private final StringBuilder mStringAccumulator = new StringBuilder();

        public ReplacementHandler(Map<String, String> replacements) {
            mReplacements = replacements;
        }

        @Override
        public void startDocument() {
            try {
                mDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) {
            Element element = mDocument.createElement(name);
            for (int i = 0; i < attributes.getLength(); i++) {
                element.setAttribute(attributes.getQName(i), attributes.getValue(i));
            }
            for (String key : mPrefixes.keySet()) {
                element.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + key, mPrefixes.get(key));
            }
            mPrefixes.clear();
            if (mCurrElement != null) {
                mElementStack.push(mCurrElement);
            }
            mCurrElement = element;
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            mPrefixes.put(prefix, uri);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            mStringAccumulator.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            if (mReplacements.containsKey(localName)) {
                mCurrElement.appendChild(mDocument.createTextNode(mReplacements.get(localName)));
            } else {
                String content = mStringAccumulator.toString().trim();
                if (!content.isEmpty()) {
                    mCurrElement.appendChild(mDocument.createTextNode(content));
                }
            }

            if (mElementStack.empty()) {
                mDocument.appendChild(mCurrElement);
                mCurrElement = null;
            } else {
                Element parent = mElementStack.pop();
                parent.appendChild(mCurrElement);
                mCurrElement = parent;
            }
            mStringAccumulator.setLength(0);
        }

        @Override
        public void error(SAXParseException e) {
            fail(e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) {
            fail(e.getMessage());
        }

        public Document getGeneratedDocument() {
            return mDocument;
        }

    }
}
