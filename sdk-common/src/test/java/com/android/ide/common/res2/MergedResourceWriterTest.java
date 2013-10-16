/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.res2;

import static com.android.ide.common.res2.MergedResourceWriter.FILENAME_PREFIX;
import static com.android.ide.common.res2.MergedResourceWriter.createPathComment;
import static com.android.utils.SdkUtils.urlToFile;

import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.XmlUtils;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.net.URL;

/** NOTE: Most of the tests for MergedResourceWriterTest are in {@link ResourceMergerTest} */
public class MergedResourceWriterTest extends TestCase {
    public void testCreatePathComment() throws Exception {
        assertEquals("From: file:/tmp/foo", createPathComment(new File("/tmp/foo")));
        assertEquals("From: file:/tmp-/%2D%2D/a%2D%2Da/foo",
                createPathComment(new File("/tmp-/--/a--a/foo")));

        String path = "/tmp/foo";
        String urlString = createPathComment(new File(path)).substring(5); // 5: "From:".length()
        assertEquals(path, urlToFile(new URL(urlString)).getPath());

        path = "/tmp-/--/a--a/foo";
        urlString = createPathComment(new File(path)).substring(5);
        assertEquals(path, urlToFile(new URL(urlString)).getPath());

        // Make sure we handle file://path too, not just file:path
        urlString = "file:///tmp-/%2D%2D/a%2D%2Da/foo";
        assertEquals(path, urlToFile(new URL(urlString)).getPath());
    }

    public void testFormattedComment() throws Exception {
        Document document = XmlUtils.parseDocumentSilently("<root/>", true);
        assertNotNull(document);
        // Many invalid characters in XML, such as -- and <, and characters invalid in URLs, such
        // as spaces
        String path = "/My Program Files/--/Q&A/X<Y/foo";
        String comment = createPathComment(new File(path));
        Element root = document.getDocumentElement();
        assertNotNull(root);
        root.appendChild(document.createComment(comment));
        String xml = XmlPrettyPrinter.prettyPrint(document, true);
        assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root> <!-- From: file:/My%20Program%20Files/%2D%2D/Q&A/X%3CY/foo -->\n"
                + "\n"
                + "</root>\n", xml);
        int index = xml.indexOf(FILENAME_PREFIX);
        assertTrue(index != -1);
        String urlString = xml.substring(index + FILENAME_PREFIX.length(),
                xml.indexOf("-->")).trim();
        assertEquals(path, urlToFile(new URL(urlString)).getPath());
    }
}
