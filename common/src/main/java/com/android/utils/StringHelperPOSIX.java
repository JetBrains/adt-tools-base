/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.utils;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * POSIX specific StringHelper that applies the following tokenization rules:
 *
 * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html
 * - A backslash that is not quoted shall preserve the literal value of the
 *   following character
 * - Enclosing characters in single-quotes ( '' ) shall preserve the literal value
 *   of each character within the single-quotes.
 * - Enclosing characters in double-quotes ( "" ) shall preserve the literal value
 *   of all characters within the double-quotes, with the exception of the
 *   characters dollar sign, backquote, and backslash
 */
public class StringHelperPOSIX extends StringHelper {

    /**
     * Quote and join a list of tokens with POSIX rules.
     *
     * @param tokens the token to be quoted and joined
     * @return the string
     */
    @NonNull
    public static String quoteAndJoinTokens(@NonNull List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            token = token.replace("\\", "\\\\");
            token = token.replace("\"", "\\\"");
            sb.append("\"").append(token).append("\" ");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Tokenize a string with POSIX rules.
     *
     * @param string the string to be tokenized
     * @return the list of tokens
     */
    @NonNull
    public static List<String> tokenizeString(@NonNull String string) {
        List<String> tokens = Lists.newArrayList();
        StringBuilder currentToken = new StringBuilder();
        boolean quoting = false;
        char quote = '\0';
        boolean escaping = false;
        boolean skipping = true;
        for (final char c : string.toCharArray()) {
            if (skipping) {
                if (Character.isWhitespace(c))
                    continue;
                else
                    skipping = false;
            }

            if (escaping) {
                escaping = false;
                if (c != '\n')
                    currentToken.append(c);
                continue;
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true;
                continue;
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true;
                quote = c;
                continue;
            } else if (quoting && c == quote) {
                quoting = false;
                quote = '\0';
                continue;
            }

            if (!quoting && Character.isWhitespace(c)) {
                skipping = true;
                if (currentToken.length() > 0)
                    tokens.add(currentToken.toString());
                currentToken.setLength(0);
                continue;
            }

            currentToken.append(c);
        }

        if (currentToken.length() > 0)
            tokens.add(currentToken.toString());

        return tokens;
    }
}
