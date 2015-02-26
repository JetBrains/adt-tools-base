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

import com.android.annotations.NonNull;
import com.android.ide.common.blame.SourceFragmentPositionRange;
import com.android.ide.common.blame.output.GradleMessage;
import com.android.ide.common.blame.output.GradleMessageRewriter;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.ide.common.res2.RecordingLogger;
import com.android.utils.ILogger;

import junit.framework.TestCase;

import java.util.List;

public class GradleMessageRewriterTest extends TestCase {

    private GradleMessageRewriter mGradleMessageRewriter;

    private ILogger mLogger;

    private PatternAwareOutputParser mFakePatternParser;

    @Override
    public void setUp() throws Exception {
        mLogger = new RecordingLogger();
        mFakePatternParser = new FakePatternAwareOutputParser();

        mGradleMessageRewriter = new GradleMessageRewriter(
                new ToolOutputParser(mFakePatternParser, mLogger),
                GradleMessageRewriter.ErrorFormatMode.MACHINE_PARSABLE);
    }

    public void testRewriteGradleMessages() {
        String original = "error example\ntwo line error\nnext line\nsomething else";
        String rewriten = mGradleMessageRewriter.rewriteMessages(original);
        String expected = "AGPBI: {\"kind\":\"ERROR\",\"text\":\"errorText\",\"sourcePath\":\"error/source\","
                + "\"position\":{\"startLine\":1,\"startColumn\":2,\"startOffset\":3,"
                + "\"endLine\":4,\"endColumn\":5,\"endOffset\":6},\"original\":\"\"}\n"
                + "AGPBI: {\"kind\":\"WARNING\",\"text\":"
                + "\"two line warning\",\"sourcePath\":\"sourcePath\","
                + "\"position\":{\"startLine\":1,\"startColumn\":2},\"original\":\"\"}\n"
                + "AGPBI: {\"kind\":\"SIMPLE\","
                + "\"text\":\"something else\",\"position\":{},\"original\":\"something else\"}";
        assertEquals(expected.trim(), rewriten.trim());
    }

    public void testParseException() {
        String original = "two line error";
        String rewriten = mGradleMessageRewriter.rewriteMessages(original);
        assertEquals(original, rewriten);
    }

    private class FakePatternAwareOutputParser implements PatternAwareOutputParser {
        @Override
        public boolean parse(@NonNull String line, @NonNull OutputLineReader reader,
                @NonNull List<GradleMessage> messages, @NonNull ILogger logger)
                throws ParsingFailedException {
            if (line.equals("two line error")) {
                String nextLine = reader.readLine();
                if ("next line".equals(nextLine)) {
                    messages.add(new GradleMessage(GradleMessage.Kind.WARNING, "two line warning",
                            "sourcePath", new SourceFragmentPositionRange(1, 2, -1), ""));
                } else {
                    throw new ParsingFailedException();
                }
                return true;
            }
            if (line.equals("error example")) {
                messages.add(
                        new GradleMessage(
                                GradleMessage.Kind.ERROR,
                                "errorText",
                                "error/source",
                                new SourceFragmentPositionRange(1, 2, 3, 4, 5, 6),
                                ""));
                return true;
            }
            return false;
        }
    }
}
