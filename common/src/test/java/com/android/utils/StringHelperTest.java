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

package com.android.utils;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for StringHelper
 */
public class StringHelperTest {

    @Test
    public void checkNoArg() throws Exception {
        assertThat(StringHelper.tokenizeCommand("a")).containsExactly("a");
    }

    @Test
    public void checkMultipleArgs() throws Exception {
        assertThat(StringHelper.tokenizeCommand("a b  c")).containsExactly("a", "b", "c");
    }

    @Test
    public void checkDoubleQuote() throws Exception {
        assertThat(StringHelper.tokenizeCommand("a \"b  c\" d")).containsExactly("a", "\"b  c\"", "d");
    }

    @Test
    public void checkSingleQuote() throws Exception {
        assertThat(StringHelper.tokenizeCommand("a 'b  c'")).containsExactly("a", "'b  c'");
    }

    @Test
    public void checkSingleQuoteWithinDoubleQuote() throws Exception {
        assertThat(StringHelper.tokenizeCommand("a \"b's  c\"")).containsExactly("a", "\"b's  c\"");
    }
}