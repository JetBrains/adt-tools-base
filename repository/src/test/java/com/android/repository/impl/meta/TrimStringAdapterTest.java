/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.impl.meta;

import junit.framework.TestCase;

/**
 * Tests for {@link TrimStringAdapter}.
 */
public class TrimStringAdapterTest extends TestCase {

    public void testSimple() {
        assertEquals("foo", evaluate("foo"));
    }

    public void testRepeatedSpace() {
        assertEquals("foo bar", evaluate("foo    \t \t\tbar"));
    }

    public void testLeadingAndTrailing() {
        assertEquals("foo", evaluate(" \tfoo  "));
    }

    public void testLeadingNewline() {
        assertEquals("foo", evaluate("\n    foo"));
    }

    public void testSingleNewlines() {
        assertEquals("foo bar baz", evaluate("foo \n bar\nbaz\n"));
    }

    public void testRepeatedNewlines() {
        assertEquals("foo\n\nbar baz", evaluate("foo\n   \n  bar\n baz"));
    }

    public void testInterning() {
        assertTrue(evaluate("foo") == evaluate("  foo\n  "));
    }

    private static String evaluate(String in) {
        return new TrimStringAdapter().unmarshal(in);
    }
}
