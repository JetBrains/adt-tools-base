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
import com.android.ide.common.blame.SourceFragmentPositionRange;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class GradleMessageRewriter {

    public static enum ErrorFormatMode {
        MACHINE_PARSABLE, HUMAN_READABLE
    }

    private final ToolOutputParser mParser;
    private final Gson mGson;
    private final ErrorFormatMode mErrorFormatMode;

    public GradleMessageRewriter(ToolOutputParser parser, ErrorFormatMode errorFormatMode) {
        mParser = parser;
        mErrorFormatMode = errorFormatMode;
        mGson = createGson(errorFormatMode);
    }

    public String rewriteMessages(@NonNull String originalMessage) {
        List<GradleMessage> messages = mParser.parseToolOutput(originalMessage);

        if (messages.isEmpty()) {
            return originalMessage;
        }

        StringBuilder errorStringBuilder = new StringBuilder();
        for (GradleMessage message: messages) {
            if (mErrorFormatMode == ErrorFormatMode.HUMAN_READABLE) {
                if (message.getPosition() != null && message.getPosition().getStartLine() != -1) {
                    errorStringBuilder.append(" Position ");
                    errorStringBuilder.append(message.getPosition().toString());
                    errorStringBuilder.append(" : ");

                }
                errorStringBuilder.append(message.getText())
                        .append("\n");

            } else {
                errorStringBuilder.append(mGson.toJson(message)).append("\n");
            }
        }
        return errorStringBuilder.toString();
    }

    private static Gson createGson(ErrorFormatMode errorFormatMode) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(SourceFragmentPositionRange.class,
                new SourceFragmentPositionRange.Serializer());
        return gsonBuilder.create();
    }
}
