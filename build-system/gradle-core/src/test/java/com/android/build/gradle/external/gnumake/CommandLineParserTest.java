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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.List;

public class CommandLineParserTest {
    private static List<String> EMPTY_ARGS = Lists.newArrayList();


    private static void assertThatAllShellResultsEquals(String target, CommandLine... expected) {
        List<CommandLine> win32 = CommandLineParser.parse(target, true);
        List<CommandLine> bash = CommandLineParser.parse(target, false);

        // Check that win32 and bash are equal
        assertThat(win32).isEqualTo(bash);

        // Check that win32 is equal to expected
        for (int i = 0; i < expected.length; ++i) {
            assertThat(win32.get(i)).isEqualTo(expected[i]);
        }
    }

    private static void assertThatEachShellResultEquals(
            String target, CommandLine expectedWin32, CommandLine expectedBash) {
        CommandLine win32 = CommandLineParser.parse(target, true).get(0);
        CommandLine bash = CommandLineParser.parse(target, false).get(0);

        assertThat(win32).isEqualTo(expectedWin32);
        assertThat(bash).isEqualTo(expectedBash);
    }

    @Test
    public void simpleCommand() throws FileNotFoundException {
        assertThatAllShellResultsEquals("ls",
                new CommandLine("ls", EMPTY_ARGS));
    }

    @Test
    public void conjunction1() throws FileNotFoundException {
        assertThatAllShellResultsEquals("ls && ls",
                new CommandLine("ls", EMPTY_ARGS),
                new CommandLine("ls", EMPTY_ARGS));
    }

    @Test
    public void conjunction2() throws FileNotFoundException {
        assertThatEachShellResultEquals("ls & ls",
                new CommandLine("ls", EMPTY_ARGS),
                new CommandLine("ls", Lists.newArrayList("&", "ls")));
    }

    @Test
    public void conjunction3() throws FileNotFoundException {
        assertThatAllShellResultsEquals("ls&&ls",
                new CommandLine("ls", EMPTY_ARGS),
                new CommandLine("ls", EMPTY_ARGS));
    }

    @Test
    public void simpleCommandWithParameter() throws FileNotFoundException {
        assertThatAllShellResultsEquals("ls -rf",
                new CommandLine("ls", Lists.newArrayList("-rf")));
    }

    @Test
    public void twoCommands() throws FileNotFoundException {
        assertThatAllShellResultsEquals("ls\nmkdir",
                new CommandLine("ls", EMPTY_ARGS),
                new CommandLine("mkdir", EMPTY_ARGS));
    }

    @Test
    public void quote1() throws FileNotFoundException {
        assertThatAllShellResultsEquals("\"a\"",
                new CommandLine("a", EMPTY_ARGS));
    }

    @Test
    public void quote2() throws FileNotFoundException {
        assertThatAllShellResultsEquals("\\\"a\\\"",
                new CommandLine("\"a\"", EMPTY_ARGS));
    }

    // Test from msvcrt example at https://msdn.microsoft.com/en-us/library/a1y7w461.aspx
    @Test
    public void msvcr1() throws FileNotFoundException {
        assertThatAllShellResultsEquals("\"a b c\" d e",
                new CommandLine("a b c", Lists.newArrayList("d", "e")));
    }

    @Test
    public void msvcrt2() throws FileNotFoundException {
        assertThatAllShellResultsEquals("\"ab\\\"c\" \"\\\\\" d",
                new CommandLine("ab\"c", Lists.newArrayList("\\", "d")));
    }

    @Test
    public void msvcrt3() throws FileNotFoundException {
        assertThatEachShellResultEquals("a\\\\\\b d\"e f\"g h",
                new CommandLine("a\\\\\\b", Lists.newArrayList("de fg", "h")),
                new CommandLine("a\\b", Lists.newArrayList("de fg", "h")));
    }

    @Test
    public void msvcrt4() throws FileNotFoundException {
        assertThatAllShellResultsEquals("a\\\\\\\"b c d",
                new CommandLine("a\\\"b", Lists.newArrayList("c", "d")));
    }

    @Test
    public void msvcrt5() throws FileNotFoundException {
        assertThatAllShellResultsEquals("a\\\\\\\\\"b c\" d e",
                new CommandLine("a\\\\b c", Lists.newArrayList("d", "e")));
    }
}