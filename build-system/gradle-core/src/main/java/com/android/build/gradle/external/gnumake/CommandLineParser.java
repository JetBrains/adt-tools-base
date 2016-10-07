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


import com.android.utils.StringHelperPOSIX;
import com.android.utils.StringHelperWindows;

import java.util.ArrayList;
import java.util.List;

/**
 * Parse a series of shell command line calls.
 */
class CommandLineParser {
    /**
     * Give a string which represents a series of shell commands (the output of ndk-build -n).
     * Token each command by splitting on spaces while observing quoting rules on the specified
     * platform.
     *
     * The result is a list of {@link CommandLine} structures. One for each command in the original
     * ndk-build output.
     */
    static List<CommandLine> parse(String commands, boolean isWin32) {
        String[] lines = commands.split("[\r\n]+");
        List<CommandLine> commandLines = new ArrayList<>();
        for (String line : lines) {
            List<String> commandList = isWin32
                    ? StringHelperWindows.splitCommandLine(line)
                    : StringHelperPOSIX.splitCommandLine(line);
            for (String commandString : commandList) {
                List<String> split = isWin32
                        ? StringHelperWindows.tokenizeString(commandString)
                        : StringHelperPOSIX.tokenizeString(commandString);
                String command = split.get(0);
                split.remove(0);
                commandLines.add(new CommandLine(command, split));
            }
        }
        return commandLines;
    }
}
