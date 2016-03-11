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

import static com.android.ide.common.res2.ValueXmlHelper.unescapeResourceString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ValueXmlHelperTest {

    @Test
    public void trim() {
        assertEquals("", unescapeResourceString("", false, true));
        assertEquals("", unescapeResourceString("  \n  ", false, true));
        assertEquals("test", unescapeResourceString("  test  ", false, true));
        assertEquals("  test  ", unescapeResourceString("\"  test  \"", false, true));
        assertEquals("test", unescapeResourceString("\n\t  test \t\n ", false, true));

        assertEquals("test\n", unescapeResourceString("  test\\n  ", false, true));
        assertEquals("  test\n  ", unescapeResourceString("\"  test\\n  \"", false, true));
        assertEquals("te\\st", unescapeResourceString("\n\t  te\\\\st \t\n ", false, true));
        assertEquals("te\\st", unescapeResourceString("  te\\\\st  ", false, true));
        assertEquals("test", unescapeResourceString("\"\"\"test\"\"  ", false, true));
        assertEquals("t t", unescapeResourceString("  \"\"t   t\"  ", false, true));
        assertEquals("t   t", unescapeResourceString("  \"\"\"t   t\"  ", false, true));
        assertEquals("\"test\"", unescapeResourceString("\"\"\\\"test\\\"\"  ", false, true));
        assertEquals("test ", unescapeResourceString("test\\  ", false, true));
        assertEquals("\\\\\\", unescapeResourceString("\\\\\\\\\\\\ ", false, true));
        assertEquals("\\\\\\ ", unescapeResourceString("\\\\\\\\\\\\\\ ", false, true));
    }

    @Test
    public void noTrim() {
        assertEquals("", unescapeResourceString("", false, false));
        assertEquals("  \n  ", unescapeResourceString("  \n  ", false, false));
        assertEquals("  test  ", unescapeResourceString("  test  ", false, false));
        assertEquals("\"  test  \"", unescapeResourceString("\"  test  \"", false, false));
        assertEquals("\n\t  test \t\n ", unescapeResourceString("\n\t  test \t\n ", false, false));

        assertEquals("  test\n  ", unescapeResourceString("  test\\n  ", false, false));
        assertEquals("\"  test\n  \"", unescapeResourceString("\"  test\\n  \"", false, false));
        assertEquals("\n\t  te\\st \t\n ",
                unescapeResourceString("\n\t  te\\\\st \t\n ", false, false));
        assertEquals("  te\\st  ", unescapeResourceString("  te\\\\st  ", false, false));
        assertEquals("\"\"\"test\"\"  ", unescapeResourceString("\"\"\"test\"\"  ", false, false));
        assertEquals("\"\"\"test\"\"  ",
                unescapeResourceString("\"\"\\\"test\\\"\"  ", false, false));
        assertEquals("test  ", unescapeResourceString("test\\  ", false, false));
        assertEquals("\\\\\\ ", unescapeResourceString("\\\\\\\\\\\\ ", false, false));
        assertEquals("\\\\\\ ", unescapeResourceString("\\\\\\\\\\\\\\ ", false, false));
    }

    @Test
    public void unescapeStringShouldUnescapeXmlSpecialCharacters() {
        assertEquals("&lt;", unescapeResourceString("&lt;", false, true));
        assertEquals("&gt;", unescapeResourceString("&gt;", false, true));
        assertEquals("<", unescapeResourceString("&lt;", true, true));
        assertEquals("<", unescapeResourceString("  &lt;  ", true, true));
        assertEquals("\"", unescapeResourceString("  &quot;  ", true, true));
        assertEquals("'", unescapeResourceString("  &apos;  ", true, true));
        assertEquals(">", unescapeResourceString("  &gt;  ", true, true));
        assertEquals("&amp;", unescapeResourceString("&amp;", false, true));
        assertEquals("&", unescapeResourceString("&amp;", true, true));
        assertEquals("&", unescapeResourceString("  &amp;  ", true, true));
        assertEquals("!<", unescapeResourceString("!&lt;", true, true));
    }

    @Test
    public void unescapeStringShouldUnescapeQuotes() {
        assertEquals("'", unescapeResourceString("\\'", false, true));
        assertEquals("\"", unescapeResourceString("\\\"", false, true));
        assertEquals(" ' ", unescapeResourceString("\" ' \"", false, true));
    }

    @Test
    public void unescapeStringShouldPreserveWhitespace() {
        assertEquals("at end  ", unescapeResourceString("\"at end  \"", false, true));
        assertEquals("  at begin", unescapeResourceString("\"  at begin\"", false, true));
    }

    @Test
    public void unescapeStringShouldUnescapeAtSignAndQuestionMarkOnlyAtBeginning() {
        assertEquals("@text", unescapeResourceString("\\@text", false, true));
        assertEquals("a@text", unescapeResourceString("a@text", false, true));
        assertEquals("?text", unescapeResourceString("\\?text", false, true));
        assertEquals("a?text", unescapeResourceString("a?text", false, true));
        assertEquals(" ?text", unescapeResourceString("\" ?text\"", false, true));
    }

    @Test
    public void unescapeStringShouldUnescapeJavaUnescapeSequences() {
        assertEquals("\n", unescapeResourceString("\\n", false, true));
        assertEquals("\t", unescapeResourceString("\\t", false, true));
        assertEquals("\\", unescapeResourceString("\\\\", false, true));
    }

    @Test
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isEscaped() {
        assertFalse(ValueXmlHelper.isEscaped("", 0));
        assertFalse(ValueXmlHelper.isEscaped(" ", 0));
        assertFalse(ValueXmlHelper.isEscaped(" ", 1));
        assertFalse(ValueXmlHelper.isEscaped("x\\y ", 0));
        assertFalse(ValueXmlHelper.isEscaped("x\\y ", 1));
        assertTrue(ValueXmlHelper.isEscaped("x\\y ", 2));
        assertFalse(ValueXmlHelper.isEscaped("x\\y ", 3));
        assertFalse(ValueXmlHelper.isEscaped("x\\\\y ", 0));
        assertFalse(ValueXmlHelper.isEscaped("x\\\\y ", 1));
        assertTrue(ValueXmlHelper.isEscaped("x\\\\y ", 2));
        assertFalse(ValueXmlHelper.isEscaped("x\\\\y ", 3));
        assertFalse(ValueXmlHelper.isEscaped("\\\\\\\\y ", 0));
        assertTrue(ValueXmlHelper.isEscaped("\\\\\\\\y ", 1));
        assertFalse(ValueXmlHelper.isEscaped("\\\\\\\\y ", 2));
        assertTrue(ValueXmlHelper.isEscaped("\\\\\\\\y ", 3));
        assertFalse(ValueXmlHelper.isEscaped("\\\\\\\\y ", 4));
    }

    @Test
    public void rewriteSpaces() {
        // Ensure that \n's in the input are rewritten as spaces, and multiple spaces
        // collapsed into a single one
        assertEquals("This is a test",
                unescapeResourceString("This is\na test", true, true));
        assertEquals("This is a test",
                unescapeResourceString("This is\n   a    test\n  ", true, true));
        assertEquals("This is\na test",
                unescapeResourceString("\"This is\na test\"", true, true));
        assertEquals("Multiple words",
                unescapeResourceString("Multiple    words", true, true));
        assertEquals("Multiple    words",
                unescapeResourceString("\"Multiple    words\"", true, true));
        assertEquals("This is a\n test",
                unescapeResourceString("This is a\\n test", true, true));
        assertEquals("This is a\n test",
                unescapeResourceString("This is\n a\\n test", true, true));
    }

    @Test
    public void htmlEntities() {
        assertEquals("Entity \u00a9 \u00a9 Copyright",
                unescapeResourceString("Entity &#169; &#xA9; Copyright", true, true));
    }

    @Test
    public void markupConcatenation() {
        assertEquals("<b>Sign in</b> or register",
                unescapeResourceString("\n   <b>Sign in</b>\n      or register\n", true, true));
    }
}
