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
package com.android.build.gradle.external.gnumake;


import java.util.ArrayList;
import java.util.List;


/**
 * Parse a series of shell command line calls.
 */
class CommandLineParser {

    /**
     * Give a string which represents a series of shell commands (the output of ndk-build -n).
     * Token each command by splitting on spaces while observing quoting rules on the current
     * platform.
     *
     * The result is a list of {@link CommandLine} structures. One for each command in the original
     * ndk-build output.
     */
    static List<CommandLine> parse(String commands, boolean isWin32) {
        String[] lines = commands.split("[\r\n]+");
        List<CommandLine> commandLines = new ArrayList<CommandLine>();
        for (String line : lines) {
            List<List<String>> splits = splitOnConjunction(tokenize(line, isWin32), isWin32);
            for (List<String> split : splits) {
                String command = split.get(0);
                split.remove(0);
                commandLines.add(new CommandLine(command, split));
            }
        }
        return commandLines;
    }

    private static boolean isShellConjunction(String string, boolean isWin32) {
        if (isWin32) {
            return string.equals("&&")
                    || string.equals("&")
                    || string.equals("||");
        } else {
            return string.equals("&&")
                    || string.equals(";")
                    || string.equals("||");
        }
    }

    private static List<String> tokenize(String line, boolean isWin32) {
        List<String> tokens = new ArrayList<String>();

        boolean quoting = false;
        boolean skipping = true;
        int slashCount = 0;
        String current = "";

        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (skipping) {
                if (Character.isWhitespace(c)) {
                    continue;
                } else {
                    skipping = false;
                }
            }
            if (isWin32) {
                // Win32: Microsoft C startup rules.
                //
                // - Arguments are delimited by white space, which is either a space or a tab.
                // - A string surrounded by double quotation marks is interpreted as a single
                //   argument, regardless of white space contained within. A quoted string can be
                //   embedded in an argument. Note that the caret (^) is not recognized as an escape
                //   character or delimiter.
                // - A double quotation mark preceded by a backslash, \", is interpreted as a
                //   literal double quotation mark (").
                // - Backslashes are interpreted literally, unless they immediately precede a double
                //   quotation mark.
                //        - If an even number of backslashes is followed by a double quotation mark,
                //          then one backslash (\) is placed in the argv array for every pair of
                //          backslashes (\\), and the double quotation mark (") is interpreted as a
                //          string delimiter.
                //        - If an odd number of backslashes is followed by a double quotation mark,
                //          then one backslash (\) is placed in the argv array for every pair of
                //          backslashes (\\) and the double quotation mark is interpreted as an
                //          escape sequence by the remaining backslash, causing a literal double
                //          quotation mark (") to be placed in argv.
                //
                if (c == '"') {
                    current = current.substring(
                            0,
                            current.length() - (slashCount + 1) / 2);
                    if (slashCount % 2 == 0) {
                        quoting = !quoting;
                    } else {
                        current += c;
                    }
                    slashCount = 0;
                    continue;
                }
                if (c == '\\') {
                    slashCount++;
                } else {
                    slashCount = 0;
                }
            } else {
                // Bash: Here slashCount can have value 0 (escape was not last character) or 1
                // (escape was last character).
                if (slashCount > 0) {
                    current += c;
                    slashCount = 0;
                    continue;
                }
                if (c == '\\') {
                    slashCount = 1;
                    continue;
                }
                if (c == '"') {
                    quoting = !quoting;
                    continue;
                }
            }
            if (!quoting && Character.isWhitespace(c)) {
                skipping = true;
                tokens.add(current);
                current = "";
                continue;
            }

            current += c;
        }
        if (!skipping) {
            tokens.add(current);
        }
        return tokens;
    }

    private static List<List<String>> splitOnConjunction(List<String> args, boolean isWin32) {
        List<List<String>> result = new ArrayList<List<String>>();
        List<String> tokens = new ArrayList<String>();

        for (int i = 0; i < args.size(); ++i) {
            String arg = args.get(i);
            if (isShellConjunction(arg, isWin32)) {
                result.add(tokens);
                tokens = new ArrayList<String>();
            } else {
                tokens.add(arg);
            }
        }
        result.add(tokens);
        return result;
    }
}
