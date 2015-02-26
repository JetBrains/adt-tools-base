/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.builder.internal;

import com.android.utils.NullLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;

@SuppressWarnings("javadoc")
public class SymbolLoaderTest extends TestCase {
    public void test() throws Exception {
        String r = "" +
                "int xml authenticator 0x7f040000\n";
        File file = File.createTempFile(getClass().getSimpleName(), "txt");
        file.deleteOnExit();
        Files.write(r, file, Charsets.UTF_8);
        SymbolLoader loader = new SymbolLoader(file, NullLogger.getLogger());
        loader.load();
        Table<String, String, SymbolLoader.SymbolEntry> symbols = loader.getSymbols();
        assertNotNull(symbols);
        assertEquals(1, symbols.size());
        assertNotNull(symbols.get("xml", "authenticator"));
        assertEquals("0x7f040000", symbols.get("xml", "authenticator").getValue());
    }

    public void testStyleables() throws Exception {
        String r = "" +
            "int[] styleable LimitedSizeLinearLayout { 0x7f010000, 0x7f010001 }\n" +
            "int styleable LimitedSizeLinearLayout_max_height 1\n" +
            "int styleable LimitedSizeLinearLayout_max_width 0\n" +
            "int xml authenticator 0x7f040000\n";
        File file = File.createTempFile(getClass().getSimpleName(), "txt");
        file.deleteOnExit();
        Files.write(r, file, Charsets.UTF_8);
        SymbolLoader loader = new SymbolLoader(file, NullLogger.getLogger());
        loader.load();
        Table<String, String, SymbolLoader.SymbolEntry> symbols = loader.getSymbols();
        assertNotNull(symbols);
        assertEquals(4, symbols.size());
    }
}
