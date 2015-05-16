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
package com.android.ide.common.blame.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.ide.common.blame.Message;
import com.android.utils.StdLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link LegacyNdkOutputParser}.
 */
public class LegacyNdkOutputParserTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private ToolOutputParser mParser;

    private File mSourceFile;

    @Before
    public void setUp() throws IOException {
        mParser = new ToolOutputParser(new LegacyNdkOutputParser(),
                new StdLogger(StdLogger.Level.VERBOSE));
        mSourceFile = mTemporaryFolder.newFile();
    }

    @Test
    public void testParse() throws Exception {
        String err = mSourceFile.getPath() +
          ":35:18: fatal error: fake.h: No such file or directory\n" +
          " #include \"fake.h\"\n" +
          "                  ^";
        List<Message> messages = mParser.parseToolOutput(err);
        assertEquals("[message count]", 1, messages.size());
        Message message = messages.iterator().next();
        assertNotNull(message);

        assertEquals("fake.h: No such file or directory", message.getText());
        assertEquals(Message.Kind.ERROR, message.getKind());
        assertEquals(35, message.getLineNumber());
        assertEquals(18, message.getColumn());
    }
}