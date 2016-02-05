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
import static org.junit.Assert.assertTrue;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.utils.StdLogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;

/**
 * Tests for {@link ToolOutputParser}.
 */
public class DexParserTest {

    private static final ToolOutputParser PARSER = new ToolOutputParser(
            new DexParser(),
            new StdLogger(StdLogger.Level.VERBOSE));

    @Test
    public void checkExceptionFormatParsedCorrectly() {
        String stderr = "\n"
                + "UNEXPECTED TOP-LEVEL EXCEPTION:\n"
                + "com.android.dex.DexIndexOverflowException: method ID not in [0, 0xffff]: 65536\n"
                + "\tat com.android.dx.merge.DexMerger$6.updateIndex(DexMerger.java:502)\n"
                + "\tat com.android.dx.merge.DexMerger$IdMerger.mergeSorted(DexMerger.java:277)\n"
                + "\tat com.android.dx.merge.DexMerger.mergeMethodIds(DexMerger.java:491)\n"
                + "\tat com.android.dx.merge.DexMerger.mergeDexes(DexMerger.java:168)\n"
                + "\tat com.android.dx.merge.DexMerger.merge(DexMerger.java:189)\n"
                + "\tat com.android.dx.command.dexer.Main.mergeLibraryDexBuffers(Main.java:502)\n"
                + "\tat com.android.dx.command.dexer.Main.runMonoDex(Main.java:334)\n"
                + "\tat com.android.dx.command.dexer.Main.run(Main.java:277)\n"
                + "\tat com.android.dx.command.dexer.Main.main(Main.java:245)\n"
                + "\tat com.android.dx.command.Main.main(Main.java:106)\n"
                + "Caused by: com.example.SomeOtherException: someOtherCause\n"
                + "\tat com.example.SomeOtherException.throw(SomeOtherException.java:34)\n"
                + "\tat com.android.dex.merge.DexMerger.merxeMethodIds(DexMerger.java:490)\n"
                + "\t... 20 more\n\n";

        Message message = Iterables.getOnlyElement(PARSER.parseToolOutput(stderr));

        assertEquals(Message.Kind.ERROR, message.getKind());
        assertEquals(DexParser.DEX_LIMIT_EXCEEDED_ERROR, message.getText());
        assertEquals(stderr.trim(), message.getRawMessage().trim());
        assertEquals(ImmutableList.of(SourceFilePosition.UNKNOWN),
                message.getSourceFilePositions());
        assertEquals(Optional.of(DexParser.DEX_TOOL_NAME), message.getToolName());
    }

    @Test
    public void checkExplanationParsedCorrectly() {
        String stderr = "\n"
                + "trouble writing output: Too many method references: 130016; max is 65536.\n"
                + "You may try using --multi-dex option.\n"
                + "References by package:\n"
                + "     2 android.app\n"
                + "130002 com.example\n"
                + "    10 com.example.helloworld\n"
                + "     2 java.lang\n\n";

        Message message = Iterables.getOnlyElement(PARSER.parseToolOutput(stderr));

        assertEquals(Message.Kind.ERROR, message.getKind());
        assertTrue(message.getText().startsWith(DexParser.DEX_LIMIT_EXCEEDED_ERROR));
        assertEquals(stderr.trim(), message.getRawMessage().trim());
        assertEquals(ImmutableList.of(SourceFilePosition.UNKNOWN),
                message.getSourceFilePositions());
        assertEquals(Optional.of(DexParser.DEX_TOOL_NAME), message.getToolName());
    }


}
