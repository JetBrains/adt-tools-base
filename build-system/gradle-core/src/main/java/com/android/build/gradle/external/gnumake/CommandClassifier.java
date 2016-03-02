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


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Find compiler commands (g++, gcc, clang) and extract inputs and outputs according to the command
 * line rules of that tool.
 */
class CommandClassifier {

    private static final BuildTool[] classifiers = {
            new NativeCompilerBuildTool(),
            new GccArBuildTool()
    };

    /**
     * Give a string that contains a list of commands recognize the interesting calls and record
     * information about inputs and outputs.
     */
    static List<BuildStepInfo> classify(String commands, boolean isWin32) {
        List<CommandLine> commandLines = CommandLineParser.parse(commands, isWin32);

        List<BuildStepInfo> commandSummaries = new ArrayList<BuildStepInfo>();

        for (CommandLine expr : commandLines) {
            for (BuildTool classifier : classifiers) {
                if (classifier.isMatch(expr)) {
                    BuildStepInfo buildStepInfo = classifier.createCommand(expr);
                    if (buildStepInfo != null) {
                        commandSummaries.add(buildStepInfo);
                    }
                }
            }
        }
        return commandSummaries;
    }

    private interface BuildTool {
        BuildStepInfo createCommand(CommandLine command);

        boolean isMatch(CommandLine command);
    }

    /**
     * This build tool matches gcc-ar (the android NDK archiver).
     * We care about the cases where the command specifies 'c' for create.
     * In this case, we pull out the inputs (typically .o) and output (.a).
     */
    static class GccArBuildTool implements BuildTool {
        private static final OptionParser PARSER = new OptionParser("cSE");

        static {
            PARSER.accepts("plugin").withRequiredArg();
            PARSER.accepts("target").withRequiredArg();
            PARSER.accepts("X32_64");
            PARSER.accepts("p").withOptionalArg();
        }


        private static void checkValidInput(String arg) {
            if (!arg.endsWith(".o")) {
                throw new RuntimeException(arg);
            }
        }

        private static void checkValidOutput(String arg) {
            if (!arg.endsWith(".a")) {
                throw new RuntimeException(arg);
            }
        }

        @Override
        public BuildStepInfo createCommand(CommandLine command) {
            String[] arr = new String[command.args.size()];
            arr = command.args.toArray(arr);
            List<String> options = (List<String>) PARSER.parse(arr).nonOptionArguments();
            if (options.size() < 3) {
                // Not enough space for <command> <archive> <input>
                return null;
            }

            if (!options.get(0).contains("c")) { // Only care about 'create'
                return null;
            }

            List<String> inputs = new ArrayList<String>();
            List<String> outputs = new ArrayList<String>();

            String output = options.get(1);
            checkValidOutput(output);
            outputs.add(output);

            for (int i = 2; i < options.size(); ++i) {
                String arg = options.get(i);
                checkValidInput(arg);
                inputs.add(arg);
            }
            return new BuildStepInfo(command, inputs, outputs);
        }

        @Override
        public boolean isMatch(CommandLine command) {
            return command.command.endsWith("gcc-ar");
        }
    }

    /**
     * This build tool matches gcc, g++ and clang. Inputs may be like .c, .cpp, etc in the case
     * that the tool is used as a compiler. Inputs may also be like .o in the case that the tool
     * is used as a linker. Output may be like .o or .so respectively.
     */
    static class NativeCompilerBuildTool implements BuildTool {
        private static final OptionParser PARSER = new OptionParser("cSE");

        // These are the gcc/clang flags that take a following parameter. This list is gleaned
        // from the clang sources.
        private static final List<String> ignoreOneFlags = Arrays.asList(
                "--assert",
                "--define-macro",
                "--dump",
                "--imacros",
                "--include",
                "--include-directory",
                "--include-directory-after",
                "--include-prefix",
                "--include-with-prefix",
                "--include-with-prefix-after",
                "--include-with-prefix-before",
                "--output",
                "--output-pch",
                "--undefine-macro",
                "--write-dependencies",
                "--write-user-dependencies",
                "-A",
                "-D",
                "-F",
                "-I",
                "-MD",
                "-MF",
                "-MMD",
                "-MQ",
                "-MT",
                "-U",
                "-idirafter",
                "-imacros",
                "-imultilib",
                "-include",
                "-iprefix",
                "-isysroot",
                "-isystem",
                "-iquote",
                "-iwithprefix",
                "-iwithprefixbefore",
                "-o");

        static {
            // Also allow flags that we don't recognize for a limited amount of future-proofing.
            PARSER.allowsUnrecognizedOptions();

            // These are the flags that require an argument.
            for (String flag :ignoreOneFlags) {
                PARSER.accepts(flag.substring(flag.lastIndexOf('-')+1)).withRequiredArg();
            }
        }

        @Override
        public BuildStepInfo createCommand(CommandLine command) {
            String[] arr = new String[command.args.size()];
            arr = command.args.toArray(arr);
            OptionSet options = PARSER.parse(arr);

            List<String> inputs = new ArrayList<String>();
            List<String> outputs = new ArrayList<String>();

            List<String> nonOptions = (List<String>) options.nonOptionArguments();
            for (String nonOption : nonOptions) {
                // Inputs are whatever is left over that doesn't look like a flag.
                if (!nonOption.startsWith("-")) {
                    inputs.add(nonOption);
                }
            }

            // Check -o
            if (options.has("o") && options.hasArgument("o")) {
                outputs.add((String) options.valueOf("o"));
            }

            // Figure out whether this command can supply terminal.
            // The -c, -S and -E flags indicate this case.
            boolean canSupplyTerminal = options.has("c")
                || options.has("S")
                || options.has("E");

            return new BuildStepInfo(command, inputs, outputs, canSupplyTerminal);
        }

        @Override
        public boolean isMatch(CommandLine command) {
            String executable = new File(command.command).getName();
            return executable.endsWith("gcc")
                    || executable.endsWith("g++")
                    || executable.endsWith("clang")
                    || executable.contains("-gcc-")
                    || executable.contains("-g++-")
                    || executable.contains("-clang-");
        }
    }
}
