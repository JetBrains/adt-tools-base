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

package com.android.build.gradle.shrinker;

import com.android.annotations.NonNull;
import com.android.build.gradle.shrinker.parser.Flags;
import com.android.build.gradle.shrinker.parser.GrammarActions;

import org.antlr.runtime.RecognitionException;

import java.io.File;
import java.io.IOException;

/**
 * Stub of a real parser. Only checks the most simple rules produced by AAPT.
 */
public class ProguardConfig {

    private final Flags mFlags = new Flags();

    public void parse(@NonNull File configFile) throws IOException {
        try {
            GrammarActions.parse(configFile, mFlags);
        } catch (RecognitionException e) {
            throw new RuntimeException(e);
        }
    }

    public void parse(@NonNull String input) throws IOException {
        try {
            GrammarActions.parse(input, mFlags);
        } catch (RecognitionException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public Flags getFlags() {
        return mFlags;
    }
}
