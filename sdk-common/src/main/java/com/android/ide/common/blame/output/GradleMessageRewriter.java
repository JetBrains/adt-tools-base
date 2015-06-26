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
package com.android.ide.common.blame.output;

import com.android.annotations.NonNull;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageJsonSerializer;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class GradleMessageRewriter {

    public static final String STDOUT_ERROR_TAG = "AGPBI: ";

    public enum ErrorFormatMode {
        MACHINE_PARSABLE, HUMAN_READABLE
    }

    private final ToolOutputParser mParser;
    private final Gson mGson;
    private final ErrorFormatMode mErrorFormatMode;

    public GradleMessageRewriter(ToolOutputParser parser, ErrorFormatMode errorFormatMode) {
        mParser = parser;
        mErrorFormatMode = errorFormatMode;
        mGson = createGson();
    }

    public String rewriteMessages(@NonNull String originalMessage) {
        List<Message> messages = mParser.parseToolOutput(originalMessage);

        if (messages.isEmpty()) {
            return originalMessage;
        }

        StringBuilder errorStringBuilder = new StringBuilder();
        for (Message message: messages) {
            if (mErrorFormatMode == ErrorFormatMode.HUMAN_READABLE) {
                for (SourceFilePosition pos : message.getSourceFilePositions()) {
                    errorStringBuilder.append(pos.toString());
                    errorStringBuilder.append(' ');
                }
                if (errorStringBuilder.length() > 0) {
                    errorStringBuilder.append(": ");
                }
                errorStringBuilder.append(message.getText()).append("\n");

            } else {
                errorStringBuilder.append(STDOUT_ERROR_TAG)
                    .append(mGson.toJson(message)).append("\n");
            }
        }
        return errorStringBuilder.toString();
    }

    private static Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        MessageJsonSerializer.registerTypeAdapters(gsonBuilder);
        return gsonBuilder.create();
    }
}
