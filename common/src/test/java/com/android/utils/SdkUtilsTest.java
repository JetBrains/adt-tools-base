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

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class SdkUtilsTest extends TestCase {
    public void testEndsWithIgnoreCase() {
        assertTrue(SdkUtils.endsWithIgnoreCase("foo", "foo"));
        assertTrue(SdkUtils.endsWithIgnoreCase("foo", "Foo"));
        assertTrue(SdkUtils.endsWithIgnoreCase("foo", "foo"));
        assertTrue(SdkUtils.endsWithIgnoreCase("Barfoo", "foo"));
        assertTrue(SdkUtils.endsWithIgnoreCase("BarFoo", "foo"));
        assertTrue(SdkUtils.endsWithIgnoreCase("BarFoo", "foO"));

        assertFalse(SdkUtils.endsWithIgnoreCase("foob", "foo"));
        assertFalse(SdkUtils.endsWithIgnoreCase("foo", "fo"));
    }

    public void testStartsWithIgnoreCase() {
        assertTrue(SdkUtils.startsWithIgnoreCase("foo", "foo"));
        assertTrue(SdkUtils.startsWithIgnoreCase("foo", "Foo"));
        assertTrue(SdkUtils.startsWithIgnoreCase("foo", "foo"));
        assertTrue(SdkUtils.startsWithIgnoreCase("barfoo", "bar"));
        assertTrue(SdkUtils.startsWithIgnoreCase("BarFoo", "bar"));
        assertTrue(SdkUtils.startsWithIgnoreCase("BarFoo", "bAr"));

        assertFalse(SdkUtils.startsWithIgnoreCase("bfoo", "foo"));
        assertFalse(SdkUtils.startsWithIgnoreCase("fo", "foo"));
    }

    public void testStartsWith() {
        assertTrue(SdkUtils.startsWith("foo", 0, "foo"));
        assertTrue(SdkUtils.startsWith("foo", 0, "Foo"));
        assertTrue(SdkUtils.startsWith("Foo", 0, "foo"));
        assertTrue(SdkUtils.startsWith("aFoo", 1, "foo"));

        assertFalse(SdkUtils.startsWith("aFoo", 0, "foo"));
        assertFalse(SdkUtils.startsWith("aFoo", 2, "foo"));
    }

    public void testEndsWith() {
        assertTrue(SdkUtils.endsWith("foo", "foo"));
        assertTrue(SdkUtils.endsWith("foobar", "obar"));
        assertTrue(SdkUtils.endsWith("foobar", "bar"));
        assertTrue(SdkUtils.endsWith("foobar", "ar"));
        assertTrue(SdkUtils.endsWith("foobar", "r"));
        assertTrue(SdkUtils.endsWith("foobar", ""));

        assertTrue(SdkUtils.endsWith(new StringBuilder("foobar"), "bar"));
        assertTrue(SdkUtils.endsWith(new StringBuilder("foobar"), new StringBuffer("obar")));
        assertTrue(SdkUtils.endsWith("foobar", new StringBuffer("obar")));

        assertFalse(SdkUtils.endsWith("foo", "fo"));
        assertFalse(SdkUtils.endsWith("foobar", "Bar"));
        assertFalse(SdkUtils.endsWith("foobar", "longfoobar"));
    }

    public void testEndsWith2() {
        assertTrue(SdkUtils.endsWith("foo", "foo".length(), "foo"));
        assertTrue(SdkUtils.endsWith("foo", "fo".length(), "fo"));
        assertTrue(SdkUtils.endsWith("foo", "f".length(), "f"));
    }

    public void testStripWhitespace() {
        assertEquals("foo", SdkUtils.stripWhitespace("foo"));
        assertEquals("foobar", SdkUtils.stripWhitespace("foo bar"));
        assertEquals("foobar", SdkUtils.stripWhitespace("  foo bar  \n\t"));
    }

    public void testWrap() {
        String s =
            "Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or portrait)" +
            "you have to repeat the actual text (and keep it up to date when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just adding new " +
            "translations for existing string resources.";
        String wrapped = SdkUtils.wrap(s, 70, "");
        assertEquals(
            "Hardcoding text attributes directly in layout files is bad for several\n" +
            "reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or\n" +
            "portrait)you have to repeat the actual text (and keep it up to date\n" +
            "when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just\n" +
            "adding new translations for existing string resources.\n",
            wrapped);
    }

    public void testWrapPrefix() {
        String s =
            "Hardcoding text attributes directly in layout files is bad for several reasons:\n" +
            "\n" +
            "* When creating configuration variations (for example for landscape or portrait)" +
            "you have to repeat the actual text (and keep it up to date when making changes)\n" +
            "\n" +
            "* The application cannot be translated to other languages by just adding new " +
            "translations for existing string resources.";
        String wrapped = SdkUtils.wrap(s, 70, "    ");
        assertEquals(
            "Hardcoding text attributes directly in layout files is bad for several\n" +
            "    reasons:\n" +
            "    \n" +
            "    * When creating configuration variations (for example for\n" +
            "    landscape or portrait)you have to repeat the actual text (and keep\n" +
            "    it up to date when making changes)\n" +
            "    \n" +
            "    * The application cannot be translated to other languages by just\n" +
            "    adding new translations for existing string resources.\n",
            wrapped);
    }


}
