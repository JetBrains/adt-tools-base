/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class StringResourceEscaperTest {

    @Test
    public void escapeCharacterDataEmpty() {
        assertEscapedXmlEquals("", "");
    }

    @Test
    public void escapeCharacterDataDecimalReference() {
        assertEscapedXmlEquals("&#38;", "&#38;");
    }

    @Test
    public void escapeCharacterDataHexadecimalReference() {
        assertEscapedXmlEquals("&#x26;", "&#x26;");
    }

    @Test
    public void escapeCharacterDataFirstQuestionMark() {
        assertEscapedXmlEquals("\\???", "???");

        assertEscapedXmlEquals(
                "\\?<xliff:g id=\"id\">?</xliff:g>?", "?<xliff:g id=\"id\">?</xliff:g>?");
        assertEscapedXmlEquals(
                "\\?<xliff:g id=\"id\">?</xliff:g>?", "?<xliff:g id='id'>?</xliff:g>?");

        assertEscapedXmlEquals("\\?<![CDATA[?]]>?", "?<![CDATA[?]]>?");
    }

    @Test
    public void escapeCharacterDataFirstAtSign() {
        assertEscapedXmlEquals("\\@@@", "@@@");

        assertEscapedXmlEquals(
                "\\@<xliff:g id=\"id\">@</xliff:g>@", "@<xliff:g id=\"id\">@</xliff:g>@");
        assertEscapedXmlEquals(
                "\\@<xliff:g id=\"id\">@</xliff:g>@", "@<xliff:g id='id'>@</xliff:g>@");

        assertEscapedXmlEquals("\\@<![CDATA[@]]>@", "@<![CDATA[@]]>@");
    }

    @Test
    public void escapeCharacterDataQuotationMarks() {
        assertEscapedXmlEquals("\\\"", "\"");

        assertEscapedXmlEquals(
                "\\\"<xliff:g id=\"id\">\\\"</xliff:g>\\\"", "\"<xliff:g id=\"id\">\"</xliff:g>\"");
        assertEscapedXmlEquals(
                "\\\"<xliff:g id=\"id\">\\\"</xliff:g>\\\"", "\"<xliff:g id='id'>\"</xliff:g>\"");

        assertEscapedXmlEquals("\\\"<![CDATA[\"]]>\\\"", "\"<![CDATA[\"]]>\"");
    }

    @Test
    public void escapeCharacterDataBackslashes() {
        assertEscapedXmlEquals("\\\\", "\\");

        assertEscapedXmlEquals(
                "\\\\<xliff:g id=\"id\">\\\\</xliff:g>\\\\", "\\<xliff:g id=\"id\">\\</xliff:g>\\");
        assertEscapedXmlEquals(
                "\\\\<xliff:g id=\"id\">\\\\</xliff:g>\\\\", "\\<xliff:g id='id'>\\</xliff:g>\\");

        assertEscapedXmlEquals("\\\\<![CDATA[\\]]>\\\\", "\\<![CDATA[\\]]>\\");
    }

    @Test
    public void escapeCharacterDataNewlines() {
        assertEscapedXmlEquals("\\n", "\n");

        assertEscapedXmlEquals(
                "\\n<xliff:g id=\"id\">\\n</xliff:g>\\n", "\n<xliff:g id=\"id\">\n</xliff:g>\n");
        assertEscapedXmlEquals(
                "\\n<xliff:g id=\"id\">\\n</xliff:g>\\n", "\n<xliff:g id='id'>\n</xliff:g>\n");

        assertEscapedXmlEquals("\\n<![CDATA[\n]]>\\n", "\n<![CDATA[\n]]>\n");
    }

    @Test
    public void escapeCharacterDataTabs() {
        assertEscapedXmlEquals("\\t", "\t");

        assertEscapedXmlEquals(
                "\\t<xliff:g id=\"id\">\\t</xliff:g>\\t", "\t<xliff:g id=\"id\">\t</xliff:g>\t");
        assertEscapedXmlEquals(
                "\\t<xliff:g id=\"id\">\\t</xliff:g>\\t", "\t<xliff:g id='id'>\t</xliff:g>\t");

        assertEscapedXmlEquals("\\t<![CDATA[\t]]>\\t", "\t<![CDATA[\t]]>\t");
    }

    @Test
    public void escapeCharacterDataApostrophesLeadingAndTrailingSpaces() {
        assertEscapedXmlEquals("\\'", "'");
        assertEscapedXmlEquals("\"' \"", "' ");
        assertEscapedXmlEquals("\" '\"", " '");
        assertEscapedXmlEquals("\" ' \"", " ' ");

        assertEscapedXmlEquals(
                "\\'<xliff:g id=\"id\">\\'</xliff:g>\\'", "'<xliff:g id=\"id\">'</xliff:g>'");
        assertEscapedXmlEquals(
                "\"'<xliff:g id=\"id\">'</xliff:g>' \"", "'<xliff:g id=\"id\">'</xliff:g>' ");
        assertEscapedXmlEquals(
                "\" '<xliff:g id=\"id\">'</xliff:g>'\"", " '<xliff:g id=\"id\">'</xliff:g>'");
        assertEscapedXmlEquals(
                "\" '<xliff:g id=\"id\">'</xliff:g>' \"", " '<xliff:g id=\"id\">'</xliff:g>' ");

        assertEscapedXmlEquals(
                "\\'<xliff:g id=\"id\">\\'</xliff:g>\\'", "'<xliff:g id='id'>'</xliff:g>'");
        assertEscapedXmlEquals(
                "\"'<xliff:g id=\"id\">'</xliff:g>' \"", "'<xliff:g id='id'>'</xliff:g>' ");
        assertEscapedXmlEquals(
                "\" '<xliff:g id=\"id\">'</xliff:g>'\"", " '<xliff:g id='id'>'</xliff:g>'");
        assertEscapedXmlEquals(
                "\" '<xliff:g id=\"id\">'</xliff:g>' \"", " '<xliff:g id='id'>'</xliff:g>' ");

        assertEscapedXmlEquals("\\'<![CDATA[']]>\\'", "'<![CDATA[']]>'");
        assertEscapedXmlEquals("\"'<![CDATA[']]>' \"", "'<![CDATA[']]>' ");
        assertEscapedXmlEquals("\" '<![CDATA[']]>'\"", " '<![CDATA[']]>'");
        assertEscapedXmlEquals("\" '<![CDATA[']]>' \"", " '<![CDATA[']]>' ");
    }

    @Test
    public void escapeCharacterDataInvalidXml() {
        try {
            StringResourceEscaper.escapeCharacterData("<");
            fail();
        } catch (IllegalArgumentException exception) {
            // Expected
        }
    }

    @Test
    public void escapeCharacterDataEntities() {
        assertEscapedXmlEquals("&amp;", "&amp;");
        assertEscapedXmlEquals("&apos;", "&apos;");
        assertEscapedXmlEquals("&gt;", "&gt;");
        assertEscapedXmlEquals("&lt;", "&lt;");
        assertEscapedXmlEquals("&quot;", "&quot;");
    }

    @Test
    public void escapeCharacterDataEmptyElement() {
        assertEscapedXmlEquals("<br/>", "<br/>");
    }

    @Test
    public void escapeCharacterDataComment() {
        assertEscapedXmlEquals("<!-- This is a comment -->", "<!-- This is a comment -->");
    }

    @Test
    public void escapeCharacterDataProcessingInstruction() {
        assertEscapedXmlEquals(
                "<?xml-stylesheet type=\"text/css\" href=\"style.css\"?>",
                "<?xml-stylesheet type=\"text/css\" href=\"style.css\"?>");
    }

    private static void assertEscapedXmlEquals(String expectedEscapedXml, String xml) {
        assertEquals(expectedEscapedXml, StringResourceEscaper.escapeCharacterData(xml));
    }

    @Test
    public void escapeEmpty() {
        assertEscapedStringEquals("", "", true);
    }

    @Test
    public void escapeFirstQuestionMark() {
        assertEscapedStringEquals("\\???", "???", true);
    }

    @Test
    public void escapeFirstAtSign() {
        assertEscapedStringEquals("\\@@@", "@@@", true);
    }

    @Test
    public void escapeQuotationMarks() {
        assertEscapedStringEquals("\\\"", "\"", true);
    }

    @Test
    public void escapeBackslashes() {
        assertEscapedStringEquals("\\\\", "\\", true);
    }

    @Test
    public void escapeNewlines() {
        assertEscapedStringEquals("\\n", "\n", true);
    }

    @Test
    public void escapeTabs() {
        assertEscapedStringEquals("\\t", "\t", true);
    }

    @Test
    public void escapeApostrophesLeadingAndTrailingSpaces() {
        assertEscapedStringEquals("\\'", "'", true);
        assertEscapedStringEquals("\"' \"", "' ", true);
        assertEscapedStringEquals("\" '\"", " '", true);
        assertEscapedStringEquals("\" ' \"", " ' ", true);
    }

    @Test
    public void escapeAmpersands() {
        assertEscapedStringEquals("&amp;", "&", true);
        assertEscapedStringEquals("&", "&", false);
    }

    @Test
    public void escapeLessThanSigns() {
        assertEscapedStringEquals("&lt;", "<", true);
        assertEscapedStringEquals("<", "<", false);
    }

    private static void assertEscapedStringEquals(String expectedEscapedString, String string,
            boolean escapeMarkupDelimiters) {
        String actualEscapedString = StringResourceEscaper.escape(string, escapeMarkupDelimiters);
        assertEquals(expectedEscapedString, actualEscapedString);
    }
}
