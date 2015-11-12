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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class DexStdoutParser implements PatternAwareOutputParser {

    @Override
    public boolean parse(@NonNull String line, @NonNull OutputLineReader reader,
            @NonNull List<Message> messages, @NonNull ILogger logger)
            throws ParsingFailedException {
        if (line.startsWith("trouble writing output: Too many method references:")) {
            StringBuilder original = new StringBuilder(line).append('\n');
            String nextLine = reader.readLine();
            while (!Strings.isNullOrEmpty(nextLine)) {
                original.append(nextLine).append('\n');
                nextLine = reader.readLine();
            }
            messages.add(new Message(
                    Message.Kind.ERROR,
                    DexStderrParser.DEX_LIMIT_EXCEEDED_ERROR,
                    original.toString(),
                    Optional.of(DexStderrParser.DEX_TOOL_NAME),
                    ImmutableList.of(SourceFilePosition.UNKNOWN)));
            return true;
        } else {
            return false;
        }
    }
}
