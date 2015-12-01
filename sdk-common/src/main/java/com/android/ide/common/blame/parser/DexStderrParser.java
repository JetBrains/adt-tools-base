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
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class DexStderrParser implements PatternAwareOutputParser {

    final DexStdoutParser delegateStdoutParser = new DexStdoutParser();
    static final String DEX_TOOL_NAME = "Dex";

    public static final String DEX_LIMIT_EXCEEDED_ERROR =
            "Your app has more methods references than can fit in a single dex file.\n"
                    + "See https://developer.android.com/tools/building/multidex.html";

    static final String COULD_NOT_CONVERT_BYTECODE_TO_DEX = "Error converting bytecode to dex:\n"
            + "Cause: %s";

    @Override
    public boolean parse(@NonNull String line, @NonNull OutputLineReader reader,
            @NonNull List<Message> messages, @NonNull ILogger logger)
            throws ParsingFailedException {

        if (delegateStdoutParser.parse(line, reader, messages, logger)) {
            return true;
        }
        if (!line.equals("UNEXPECTED TOP-LEVEL EXCEPTION:")) {
            return false;
        }
        StringBuilder original = new StringBuilder(line).append('\n');
        String exception = reader.readLine();
        if (exception == null) {
            reader.pushBack();
            return false;
        }
        if (exception.startsWith(
                "com.android.dex.DexIndexOverflowException: method ID not in [0, 0xffff]: ")) {
            original.append(exception).append('\n');
            consumeStacktrace(reader, original);
            messages.add(new Message(
                    Message.Kind.ERROR,
                    DEX_LIMIT_EXCEEDED_ERROR,
                    original.toString(),
                    Optional.of(DEX_TOOL_NAME),
                    ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        } else { // Other generic exception
            original.append(exception).append('\n');
            consumeStacktrace(reader, original);
            messages.add(new Message(
                    Message.Kind.ERROR,
                    String.format(COULD_NOT_CONVERT_BYTECODE_TO_DEX, exception),
                    original.toString(),
                    Optional.of(DEX_TOOL_NAME),
                    ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        }
    }

    private static void consumeStacktrace(OutputLineReader reader, StringBuilder out) {
        String nextLine = reader.readLine();
        while (nextLine != null && nextLine.startsWith("\t")) {
            out.append(nextLine).append('\n');
            nextLine = reader.readLine();
        }
        //noinspection VariableNotUsedInsideIf
        if (nextLine != null) {
            reader.pushBack();
        }
    }
}
