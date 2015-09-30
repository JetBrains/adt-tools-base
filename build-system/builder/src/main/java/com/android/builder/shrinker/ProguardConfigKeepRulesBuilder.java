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

package com.android.builder.shrinker;

import static com.google.common.base.Preconditions.checkState;

import com.android.builder.shrinker.parser.Flags;
import com.android.builder.shrinker.parser.GrammarActions;

import org.antlr.runtime.RecognitionException;

import java.io.File;
import java.io.IOException;

/**
 * Stub of a real parser. Only checks the most simple rules produced by AAPT.
 */
public class ProguardConfigKeepRulesBuilder {

    private final Flags mFlags = new Flags();
    private boolean done = false;

    public void parse(File configFile) throws IOException {
        checkState(!done, "getKeepRules() already called.");
        try {
            GrammarActions.parse(configFile.getPath(), ".", mFlags);
        } catch (RecognitionException e) {
            throw new RuntimeException(e);
        }
    }

    public KeepRules getKeepRules() {
        done = true;
        // TODO: check for -dontobfuscate and other required flags.
        return new ProguardFlagsKeepRules(mFlags);
    }
}
