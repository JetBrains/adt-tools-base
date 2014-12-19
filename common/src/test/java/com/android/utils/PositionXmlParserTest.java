/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.utils;

import com.android.utils.PositionXmlParser.Position;

import junit.framework.TestCase;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

@SuppressWarnings("javadoc")
public class PositionXmlParserTest extends TestCase {
    public void test() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button2\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "\n" +
                "</LinearLayout>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);
        NodeList buttons = document.getElementsByTagName("Button");
        assertEquals(2, buttons.getLength());
        final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
        assertEquals("wrap_content",
                linearLayout.getAttributeNS(ANDROID_URI, "layout_height"));

        // Check attribute positions
        Attr attr = linearLayout.getAttributeNodeNS(ANDROID_URI, "layout_width");
        assertNotNull(attr);
        Position start = parser.getPosition(attr);
        Position end = start.getEnd();
        assertEquals(2, start.getLine());
        assertEquals(4, start.getColumn());
        assertEquals(xml.indexOf("android:layout_width"), start.getOffset());
        assertEquals(2, end.getLine());
        String target = "android:layout_width=\"match_parent\"";
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check element positions
        Element button = (Element) buttons.item(0);
        start = parser.getPosition(button);
        end = start.getEnd();
        assertNull(end.getEnd());
        assertEquals(6, start.getLine());
        assertEquals(4, start.getColumn());
        assertEquals(xml.indexOf("<Button"), start.getOffset());
        assertEquals(xml.indexOf("/>") + 2, end.getOffset());
        assertEquals(10, end.getLine());
        int button1End = end.getOffset();

        Element button2 = (Element) buttons.item(1);
        start = parser.getPosition(button2);
        end = start.getEnd();
        assertEquals(12, start.getLine());
        assertEquals(4, start.getColumn());
        assertEquals(xml.indexOf("<Button", button1End), start.getOffset());
        assertEquals(xml.indexOf("/>", start.getOffset()) + 2, end.getOffset());
        assertEquals(16, end.getLine());

        file.delete();
    }

    public void testText() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "          some text\n" +
                "\n" +
                "</LinearLayout>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        file.deleteOnExit();
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);
        NodeList buttons = document.getElementsByTagName("Button");
        assertEquals(1, buttons.getLength());
        final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
        assertEquals("wrap_content",
                linearLayout.getAttributeNS(ANDROID_URI, "layout_height"));

        // Check text positions
        Element button = (Element) buttons.item(0);
        Text text = (Text) button.getNextSibling();
        assertNotNull(text);

        // Check attribute positions
        Position start = parser.getPosition(text);
        assertEquals(11, start.getLine());
        assertEquals(10, start.getColumn());
        assertEquals(xml.indexOf("some text"), start.getOffset());

        // Check attribute positions with text node offsets
        start = parser.getPosition(text, 13, 15);
        assertEquals(11, start.getLine());
        assertEquals(12, start.getColumn());
        assertEquals(xml.indexOf("me"), start.getOffset());
    }

    public void testLineEndings() throws Exception {
        // Test for http://code.google.com/p/android/issues/detail?id=22925
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
                "<LinearLayout>\r\n" +
                "\r" +
                "<LinearLayout></LinearLayout>\r\n" +
                "</LinearLayout>\r\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest2", ".xml");
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        file.delete();
    }

    private static void checkEncoding(String encoding, boolean writeBom, boolean writeEncoding,
            String lineEnding)
            throws Exception {
        // Norwegian extra vowel characters such as "latin small letter a with ring above"
        String value = "\u00e6\u00d8\u00e5";
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"");
        if (writeEncoding) {
            sb.append(" encoding=\"");
            sb.append(encoding);
            sb.append("\"");
        }
        sb.append("?>");
        sb.append(lineEnding);
        sb.append(
                "<!-- This is a " + lineEnding +
                "     multiline comment" + lineEnding +
                "-->" + lineEnding +
                "<foo ");
        int startAttrOffset = sb.length();
        sb.append("attr=\"");
        sb.append(value);
        sb.append("\"");
        sb.append(">" + lineEnding +
                lineEnding +
                "<bar></bar>" + lineEnding +
                "</foo>" + lineEnding);
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest" + encoding + writeBom + writeEncoding,
                ".xml");
        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        OutputStreamWriter writer = new OutputStreamWriter(stream, encoding);

        if (writeBom) {
            String normalized = encoding.toLowerCase().replace("-", "_");
            if (normalized.equals("utf_8")) {
                stream.write(0xef);
                stream.write(0xbb);
                stream.write(0xbf);
            } else if (normalized.equals("utf_16")) {
                stream.write(0xfe);
                stream.write(0xff);
            } else if (normalized.equals("utf_16le")) {
                stream.write(0xff);
                stream.write(0xfe);
            } else if (normalized.equals("utf_32")) {
                stream.write(0x0);
                stream.write(0x0);
                stream.write(0xfe);
                stream.write(0xff);
            } else if (normalized.equals("utf_32le")) {
                stream.write(0xff);
                stream.write(0xfe);
                stream.write(0x0);
                stream.write(0x0);
            } else {
                fail("Can't write BOM for encoding " + encoding);
            }
        }

        writer.write(sb.toString());
        writer.close();

        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);
        Element root = document.getDocumentElement();
        assertEquals(file.getPath(), value, root.getAttribute("attr"));
        assertEquals(4, parser.getPosition(root).getLine());

        Attr attribute = root.getAttributeNode("attr");
        assertNotNull(attribute);
        Position position = parser.getPosition(attribute);
        assertNotNull(position);
        assertEquals(4, position.getLine());
        assertEquals(startAttrOffset, position.getOffset());

        file.delete();
    }

    public void testEncoding() throws Exception {
        checkEncoding("utf-8", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF-8", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_16", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF-16", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_16LE", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_32", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_32LE", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("windows-1252", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("MacRoman", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("ISO-8859-1", false /*bom*/, true /*encoding*/, "\n");
        checkEncoding("iso-8859-1", false /*bom*/, true /*encoding*/, "\n");

        // Try BOM's (with no encoding specified)
        checkEncoding("utf-8", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF-8", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_16", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF-16", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_16LE", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_32", true /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_32LE", true /*bom*/, false /*encoding*/, "\n");

        // Try default encodings (only defined for utf-8 and utf-16)
        checkEncoding("utf-8", false /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF-8", false /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_16", false /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF-16", false /*bom*/, false /*encoding*/, "\n");
        checkEncoding("UTF_16LE", false /*bom*/, false /*encoding*/, "\n");

        // Try BOM's (with explicit encoding specified)
        checkEncoding("utf-8", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF-8", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_16", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF-16", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_16LE", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_32", true /*bom*/, true /*encoding*/, "\n");
        checkEncoding("UTF_32LE", true /*bom*/, true /*encoding*/, "\n");

        // Make sure this works for \r and \r\n as well
        checkEncoding("UTF-16", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("UTF_16LE", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("UTF_32", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("UTF_32LE", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("windows-1252", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("MacRoman", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("ISO-8859-1", false /*bom*/, true /*encoding*/, "\r");
        checkEncoding("iso-8859-1", false /*bom*/, true /*encoding*/, "\r");

        checkEncoding("UTF-16", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("UTF_16LE", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("UTF_32", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("UTF_32LE", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("windows-1252", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("MacRoman", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("ISO-8859-1", false /*bom*/, true /*encoding*/, "\r\n");
        checkEncoding("iso-8859-1", false /*bom*/, true /*encoding*/, "\r\n");
    }

    public void testOneLineComment() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"wrap_content\"\n" +
                        "    android:orientation=\"vertical\" >\n" +
                        "\n" +
                        "    <!-- this is a comment ! -->\n" +
                        "    <Button\n" +
                        "        android:id=\"@+id/button1\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Button\" />\n" +
                        "          some text\n" +
                        "\n" +
                        "</LinearLayout>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        file.deleteOnExit();
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);

        // first child is a comment.
        org.w3c.dom.Node commentNode = linearLayout.getFirstChild().getNextSibling();
        assertEquals(Node.COMMENT_NODE, commentNode.getNodeType());
        Position position = parser.getPosition(commentNode);
        assertNotNull(position);
        assertEquals(6, position.getLine());
        assertEquals(4, position.getColumn());
        assertEquals(xml.indexOf("<!--"), position.getOffset());

        // ensure that the next siblings' position start at the right location.
        Element button = (Element) document.getElementsByTagName("Button").item(0);
        Position buttonPosition = parser.getPosition(button);
        assertNotNull(buttonPosition);
        assertEquals(7, buttonPosition.getLine());
        assertEquals(4, buttonPosition.getColumn());
    }

    public void testMultipleLineComment() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"wrap_content\"\n" +
                        "    android:orientation=\"vertical\" >\n" +
                        "\n" +
                        "    <!-- this is a more complicated \n" +
                        "         which spans on multiple lines\n" +
                        "         in the source xml -->\n" +
                        "    <Button\n" +
                        "        android:id=\"@+id/button1\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Button\" />\n" +
                        "          some text\n" +
                        "\n" +
                        "</LinearLayout>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        file.deleteOnExit();
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);

        // first child is a comment.
        Node commentNode = linearLayout.getFirstChild().getNextSibling();
        assertEquals(Node.COMMENT_NODE, commentNode.getNodeType());
        Position position = parser.getPosition(commentNode);
        assertNotNull(position);
        assertEquals(6, position.getLine());
        assertEquals(4, position.getColumn());


        // ensure that the next siblings' position start at the right location.
        Element button = (Element) document.getElementsByTagName("Button").item(0);
        Position buttonPosition = parser.getPosition(button);
        assertNotNull(buttonPosition);
        assertEquals(9, buttonPosition.getLine());
        assertEquals(4, buttonPosition.getColumn());
    }

    public void testAttributeWithoutNamespace() throws Exception {
        // Search for attribute with different prefixes and with no prefix.
        // Make sure we find it regardless of which one comes first (which is why we
        // list all 3 orders here and search for all attributes in all 3.)
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout\n"
                + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "        xmlns:other=\"http://foo.bar\">\n"
                + "    <Button\n"
                + "        android:id=\"@+id/button1\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        other:orientation=\"vertical\"\n"
                + "        orientation=\"true\"/>\n"
                + "    <Button\n"
                + "        android:id=\"@+id/button2\"\n"
                + "        other:orientation=\"vertical\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        orientation=\"true\"/>\n"
                + "    <Button\n"
                + "        android:id=\"@+id/button3\"\n"
                + "        orientation=\"true\"\n"
                + "        android:orientation=\"vertical\"\n"
                + "        other:orientation=\"vertical\"/>\n"
                + "</LinearLayout>\n"
                + "\n";

        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        file.deleteOnExit();
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(xml);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);
        NodeList buttons = document.getElementsByTagName("Button");
        assertEquals(3, buttons.getLength());

        // Check attribute positions
        for (int i = 0, n = buttons.getLength(); i < n; i++) {
            Element button = (Element)buttons.item(i);
            Attr attr;
            Position start;
            Position end;

            attr = button.getAttributeNode("orientation");
            start = parser.getPosition(attr);
            assertNotNull(start);
            end = start.getEnd();
            assertNotNull(end);
            assertEquals(" orientation=\"true\"",
                    xml.substring(start.getOffset() - 1, end.getOffset()));

            attr = button.getAttributeNodeNS("http://schemas.android.com/apk/res/android",
                    "orientation");
            start = parser.getPosition(attr);
            assertNotNull(start);
            end = start.getEnd();
            assertNotNull(end);
            assertEquals("android:orientation=\"vertical\"",
                    xml.substring(start.getOffset(), end.getOffset()));

            attr = button.getAttributeNodeNS("http://foo.bar", "orientation");
            start = parser.getPosition(attr);
            assertNotNull(start);
            end = start.getEnd();
            assertNotNull(end);
            assertEquals("other:orientation=\"vertical\"",
                    xml.substring(start.getOffset(), end.getOffset()));
        }
    }


    public void testTagNamespace() throws Exception {

        final String NAMESPACE_URL = "http://example.org/path";
        final String XML =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<ns:SomeTag xmlns:ns=\"" + NAMESPACE_URL + "\">\n" +
                        "    <ns:SubTag\n" +
                        "        ns:text=\"Button\" />\n" +
                        "</ns:SomeTag>\n";
        PositionXmlParser parser = new PositionXmlParser();
        File file = File.createTempFile("parsertest", ".xml");
        Writer fw = new BufferedWriter(new FileWriter(file));
        fw.write(XML);
        fw.close();
        Document document = parser.parse(new FileInputStream(file));
        assertNotNull(document);
        Element e = document.getDocumentElement();
        assertNotNull(e);
        assertEquals("http://example.org/path", e.getNamespaceURI());
        assertEquals("ns:SomeTag", e.getTagName());
        assertEquals("ns:SomeTag", e.getNodeName());
        assertEquals("SomeTag",e.getLocalName());
        NodeList subTags = e.getElementsByTagNameNS(NAMESPACE_URL, "SubTag");
        assertEquals(1, subTags.getLength());
        Node subTagNode = subTags.item(0);
        assertEquals(Node.ELEMENT_NODE, subTagNode.getNodeType());
        Element subTag = (Element) subTagNode;
        assertEquals("ns:SubTag", subTag.getTagName());
        assertEquals("ns:SubTag", subTag.getNodeName());
        assertEquals("SubTag", subTag.getLocalName());
        Attr attr = subTag.getAttributeNodeNS(NAMESPACE_URL, "text");
        assertNotNull(attr);
        Position start = parser.getPosition(attr);
        assertNotNull(start);
        Position end = start.getEnd();
        assertNotNull(end);
        assertEquals("ns:text=\"Button\"", XML.substring(start.getOffset(), end.getOffset()));
        assertEquals("Button", subTag.getAttributeNS(NAMESPACE_URL, "text"));
        assertEquals(NAMESPACE_URL, subTag.getNamespaceURI());
    }
}
