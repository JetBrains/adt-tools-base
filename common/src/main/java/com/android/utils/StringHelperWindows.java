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
 * Windows specific StringHelper that applies the following tokenization rules:
 *
 * https://msdn.microsoft.com/en-us/library/17w5ykft.aspx
 * - A string surrounded by double quotation marks ("string") is interpreted as a
 *   single argument, regardless of white space contained within. A quoted string
 *   can be embedded in an argument.
 * - A double quotation mark preceded by a backslash (\") is interpreted as a
 *   literal double quotation mark character (").
 * - Backslashes are interpreted literally, unless they immediately precede a
 *   double quotation mark.
 * - If an even number of backslashes is followed by a double quotation mark, one
 *   backslash is placed in the argv array for every pair of backslashes, and the
 *   double quotation mark is interpreted as a string delimiter.
 * - If an odd number of backslashes is followed by a double quotation mark, one
 *   backslash is placed in the argv array for every pair of backslashes, and the
 *   double quotation mark is "escaped" by the remaining backslash
 */
public class StringHelperWindows extends StringHelper {

    /**
     * Quote and join a list of tokens with Windows rules.
     *
     * @param tokens the token to be quoted and joined
     * @return the string
     */
    @NonNull
    public static String quoteAndJoinTokens(@NonNull List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            token = token.replaceAll("(\\\\+)(?=\"|$)", "$1$1");
            token = token.replace("\"", "\\\"");
            sb.append("\"").append(token).append("\" ");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Tokenize a string with Windows rules.
     *
     * @param string the string to be tokenized
     * @return the list of tokens
     */
    @NonNull
    public static List<String> tokenizeString(@NonNull String string) {
        List<String> tokens = Lists.newArrayList();
        StringBuilder currentToken = new StringBuilder();
        boolean quoting = false;
        boolean escapingQuotes = false;
        boolean escapingOthers = false;
        boolean skipping = true;
        for (final char c : string.toCharArray()) {
            if (skipping) {
                if (Character.isWhitespace(c))
                    continue;
                else
                    skipping = false;
            }

            if (c == '"') {
                // delete one slash for every pair of preceding slashes
                for (int i = currentToken.length() - 2;
                        i >= 0 && currentToken.charAt(i) == '\\'
                                && currentToken.charAt(i+1) == '\\';
                        i -= 2) {
                    currentToken.deleteCharAt(i);
                }
                if (escapingQuotes) {
                    currentToken.deleteCharAt(currentToken.length() - 1);
                } else {
                    quoting = !quoting;
                    continue;
                }
            }

            if (escapingQuotes) {
                escapingQuotes = false;
            } else if (c == '\\') {
                escapingQuotes = true;
            }

            if (escapingOthers) {
                escapingOthers = false;
                if (c == '\n')
                    continue;
            } else if (!quoting && c == '^') {
                escapingOthers = true;
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
