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
package com.android.utils;

import static com.android.SdkConstants.XMLNS;

import com.android.SdkConstants;
import com.android.annotations.Nullable;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class XmlUtilsTest extends TestCase {
    public void testlookupNamespacePrefix() throws Exception {
        // Setup
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element rootElement = document.createElement("root");
        Attr attr = document.createAttributeNS(SdkConstants.XMLNS_URI,
                "xmlns:customPrefix");
        attr.setValue(SdkConstants.ANDROID_URI);
        rootElement.getAttributes().setNamedItemNS(attr);
        document.appendChild(rootElement);
        Element root = document.getDocumentElement();
        root.appendChild(document.createTextNode("    "));
        Element foo = document.createElement("foo");
        root.appendChild(foo);
        root.appendChild(document.createTextNode("    "));
        Element bar = document.createElement("bar");
        root.appendChild(bar);
        Element baz = document.createElement("baz");
        root.appendChild(baz);

        String prefix = XmlUtils.lookupNamespacePrefix(baz, SdkConstants.ANDROID_URI);
        assertEquals("customPrefix", prefix);

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/tools", "tools", false);
        assertEquals("tools", prefix);

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/apk/res/my/pkg", "app", false);
        assertEquals("app", prefix);
        assertFalse(declaresNamespace(document, "http://schemas.android.com/apk/res/my/pkg"));

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/apk/res/my/pkg", "app", true /*create*/);
        assertEquals("app", prefix);
        assertTrue(declaresNamespace(document, "http://schemas.android.com/apk/res/my/pkg"));
    }

    private static boolean declaresNamespace(Document document, String uri) {
        NamedNodeMap attributes = document.getDocumentElement().getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getName();
            if (name.startsWith(XMLNS) && uri.equals(attribute.getValue())) {
                return true;
            }
        }

        return false;
    }

    public void testToXmlAttributeValue() throws Exception {
        assertEquals("", XmlUtils.toXmlAttributeValue(""));
        assertEquals("foo", XmlUtils.toXmlAttributeValue("foo"));
        assertEquals("foo&lt;bar", XmlUtils.toXmlAttributeValue("foo<bar"));
        assertEquals("foo>bar", XmlUtils.toXmlAttributeValue("foo>bar"));

        assertEquals("&quot;", XmlUtils.toXmlAttributeValue("\""));
        assertEquals("&apos;", XmlUtils.toXmlAttributeValue("'"));
        assertEquals("foo&quot;b&apos;&apos;ar",
                XmlUtils.toXmlAttributeValue("foo\"b''ar"));
        assertEquals("&lt;&quot;&apos;>&amp;", XmlUtils.toXmlAttributeValue("<\"'>&"));
    }

    public void testAppendXmlAttributeValue() throws Exception {
        StringBuilder sb = new StringBuilder();
        XmlUtils.appendXmlAttributeValue(sb, "<\"'>&");
        assertEquals("&lt;&quot;&apos;>&amp;", sb.toString());
    }

    public void testToXmlTextValue() throws Exception {
        assertEquals("&lt;\"'>&amp;", XmlUtils.toXmlTextValue("<\"'>&"));
    }

    public void testAppendXmlTextValue() throws Exception {
        StringBuilder sb = new StringBuilder();
        XmlUtils.appendXmlTextValue(sb, "<\"'>&");
        assertEquals("&lt;\"'>&amp;", sb.toString());
    }

    public void testNew() throws Exception {
        Document doc = createEmptyPlainDocument();
        Element root = doc.createElement("myroot");
        doc.appendChild(root);
        root.setAttribute("foo", "bar");
        root.setAttribute("baz", "baz");
        Element child = doc.createElement("mychild");
        root.appendChild(child);
        Element child2 = doc.createElement("hasComment");
        root.appendChild(child2);
        Node comment = doc.createComment("This is my comment");
        child2.appendChild(comment);
        Element child3 = doc.createElement("hasText");
        root.appendChild(child3);
        Node text = doc.createTextNode("  This is my text  ");
        child3.appendChild(text);

        String xml = XmlUtils.toXml(doc, false);
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<myroot baz=\"baz\" foo=\"bar\">\n" +
                "    <mychild/>\n" +
                "    <hasComment>\n" +
                "        <!--\n" +
                "            This is my comment\n" +
                "        -->\n" +
                "    </hasComment>\n" +
                "    <hasText>\n" +
                "            This is my text\n" +
                "    </hasText>\n" +
                "</myroot>\n",
                xml);

        xml = XmlUtils.toXml(doc, true);
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<myroot baz=\"baz\" foo=\"bar\"><mychild/><hasComment><!--This is my comment--></hasComment><hasText>  This is my text  </hasText></myroot>",
               xml);

    }

    @Nullable
    private static Document createEmptyPlainDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;
        builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
