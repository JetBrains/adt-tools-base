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
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFragmentPositionRange;
import com.android.ide.common.blame.output.GradleMessage;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.AaptOutputParser;
import com.android.utils.ILogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class BlameRewritingLogger implements ILogger {

    private final ILogger mLogger;
    private final GradleMessageRewriter mGradleMessageRewriter;

    public BlameRewritingLogger(@NonNull ILogger logger, @NonNull GradleMessageRewriter.ErrorFormatMode errorFormatMode) {
        this.mLogger = logger;
        ToolOutputParser parser = new ToolOutputParser(new AaptOutputParser(), logger);
        mGradleMessageRewriter = new GradleMessageRewriter(parser, errorFormatMode);
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        mLogger.error(t, mGradleMessageRewriter.rewriteMessages(msgFormat), args);
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        mLogger.warning(mGradleMessageRewriter.rewriteMessages(msgFormat), args);
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        mLogger.info(msgFormat, args);
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        mLogger.verbose(msgFormat, args);
    }
}
