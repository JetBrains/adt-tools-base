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


import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Find compiler commands (g++, gcc, clang) and extract inputs and outputs according to the command
 * line rules of that tool.
 */
class CommandClassifier {
    @NonNull
    final private static NativeCompilerBuildTool sNativeCompilerBuildTool =
            new NativeCompilerBuildTool();
    @NonNull
    final private static GccArBuildTool sGccArBuildTool =
            new GccArBuildTool();
    @NonNull
    final private static CCacheBuildTool sCCacheBuildTool =
            new CCacheBuildTool();

    @NonNull
    private static final BuildTool[] classifiers = {
            sNativeCompilerBuildTool,
            sGccArBuildTool,
            sCCacheBuildTool
    };

    /**
     * Give a string that contains a list of commands recognize the interesting calls and record
     * information about inputs and outputs.
     */
    @NonNull
    static List<BuildStepInfo> classify(String commands, boolean isWin32) {
        List<CommandLine> commandLines = CommandLineParser.parse(commands, isWin32);

        List<BuildStepInfo> commandSummaries = new ArrayList<>();

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
        @Nullable
        BuildStepInfo createCommand(@NonNull CommandLine command);

        boolean isMatch(@NonNull CommandLine command);
    }

    /**
     * This build tool matches gcc-ar (the android NDK archiver).
     * We care about the cases where the command specifies 'c' for create.
     * In this case, we pull out the inputs (typically .o) and output (.a).
     */
    static class GccArBuildTool implements BuildTool {
        @NonNull
        private static final OptionParser PARSER = new OptionParser("cSE");

        static {
            PARSER.accepts("plugin").withRequiredArg();
            PARSER.accepts("target").withRequiredArg();
            PARSER.accepts("X32_64");
            PARSER.accepts("p").withOptionalArg();
        }


        private static void checkValidInput(@NonNull String arg) {
            if (!arg.endsWith(".o")) {
                throw new RuntimeException(arg);
            }
        }

        private static void checkValidOutput(@NonNull String arg) {
            if (!arg.endsWith(".a")) {
                throw new RuntimeException(arg);
            }
        }

        @Nullable
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            String[] arr = new String[command.args.size()];
            arr = command.args.toArray(arr);
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) PARSER.parse(arr).nonOptionArguments();
            if (options.size() < 3) {
                // Not enough space for <command> <archive> <input>
                return null;
            }

            if (!options.get(0).contains("c")) { // Only care about 'create'
                return null;
            }

            List<String> inputs = new ArrayList<>();
            List<String> outputs = new ArrayList<>();

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
        public boolean isMatch(@NonNull CommandLine command) {
            return command.executable.endsWith("gcc-ar");
        }
    }

    /**
     * A CCache command line is like:
     *
     *   /usr/bin/ccache gcc [gcc-flags]
     *
     * This build tool first looks for ccache executable and then translates it into a
     * compiler BuildStepInfo.
     */
    static class CCacheBuildTool implements BuildTool {
        @Nullable
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            CommandLine translated = translateToCompilerCommandLine(command);
            return sNativeCompilerBuildTool.createCommand(translated);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            String executable = new File(command.executable).getName();
            if (executable.endsWith("ccache")) {
                CommandLine translated = translateToCompilerCommandLine(command);
                return sNativeCompilerBuildTool.isMatch(translated);
            }
            return false;
        }

        @NonNull
        private static CommandLine translateToCompilerCommandLine(@NonNull CommandLine command) {
            List<String> args = Lists.newArrayList(command.args);
            String baseCommand = args.get(0);
            args.remove(0);
            return new CommandLine(baseCommand, args);

        }
    }

    /**
     * This build tool matches gcc, g++ and clang. Inputs may be like .c, .cpp, etc in the case
     * that the tool is used as a compiler. Inputs may also be like .o in the case that the tool
     * is used as a linker. Output may be like .o or .so respectively.
     */
    static class NativeCompilerBuildTool implements BuildTool {
        @NonNull
        private static final OptionParser PARSER = new OptionParser("cSE");

        // These are the gcc/clang flags that take a following parameter. This list is gleaned
        // from the clang sources.
        @NonNull
        private static final List<String> ignoreOneFlags = Arrays.asList(
                "assert",
                "define-macro",
                "dump",
                "gcc-toolchain",
                "idirafter",
                "imacros",
                "imultilib",
                "include",
                "include-directory",
                "include-directory-after",
                "include-prefix",
                "include-with-prefix",
                "include-with-prefix-after",
                "include-with-prefix-before",
                "iprefix",
                "isysroot",
                "isystem",
                "iquote",
                "iwithprefix",
                "iwithprefixbefore",
                "o",
                "output",
                "output-pch",
                "undefine-macro",
                "write-dependencies",
                "write-user-dependencies",
                "A",
                "D",
                "F",
                "I",
                "MD",
                "MF",
                "MMD",
                "MQ",
                "MT",
                "U",
                "target");

        static {
            // Also allow flags that we don't recognize for a limited amount of future-proofing.
            PARSER.allowsUnrecognizedOptions();

            // These are the flags that require an argument.
            for (String flag :ignoreOneFlags) {
                PARSER.accepts(flag).withRequiredArg();
            }

        }

        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            String[] arr = new String[command.args.size()];
            arr = command.args.toArray(arr);
            OptionSet options = PARSER.parse(arr);

            List<String> outputs = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> nonOptions = (List<String>) options.nonOptionArguments();
            // Inputs are whatever is left over that doesn't look like a flag.
            List<String> inputs = nonOptions.stream()
                    .filter(nonOption -> !nonOption.startsWith("-"))
                    .collect(Collectors.toList());

            // Check -o
            if (options.has("o") && options.hasArgument("o")) {
                String output = (String) options.valueOf("o");
                outputs.add(output);
            }

            // Figure out whether this command can supply terminal source file (.cpp, .c).
            // The -c, -S and -E flags indicate this case.
            boolean inputsAreSourceFiles = options.has("c")
                || options.has("S")
                || options.has("E");

            return new BuildStepInfo(command, inputs, outputs, inputsAreSourceFiles);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            String executable = new File(command.executable).getName();
            return executable.endsWith("gcc")
                    || executable.endsWith("g++")
                    || executable.endsWith("clang")
                    || executable.endsWith("clang++")
                    || executable.endsWith("clang.exe")
                    || executable.endsWith("clang++.exe")
                    || executable.contains("-gcc-")
                    || executable.contains("-g++-");
        }
    }
}
