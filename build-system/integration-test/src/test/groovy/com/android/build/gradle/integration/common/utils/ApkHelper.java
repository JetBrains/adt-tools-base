/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.utils.StdLogger;
import com.google.common.collect.Lists;

import org.gradle.api.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper to help read/test the content of generated apk file.
 */
public class ApkHelper {
    private static final Pattern PATTERN = Pattern.compile(
            "^Class descriptor\\W*:\\W*'(L.+;)'$");

    public static void checkVersion(
            @NonNull File apk,
            @Nullable Integer code)
            throws IOException, InterruptedException, LoggedErrorException {
        checkVersion(apk, code, null /* versionName */);
    }

    public static void checkVersionName(
        @NonNull File apk,
        @Nullable String name)
        throws IOException, InterruptedException, LoggedErrorException {
        checkVersion(apk, null, name);
    }

    public static void checkVersion(
            @NonNull File apk,
            @Nullable Integer code,
            @Nullable String versionName)
            throws IOException, InterruptedException, LoggedErrorException {
        CommandLineRunner commandLineRunner = new CommandLineRunner(
                new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), commandLineRunner);

        ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apk);

        if (code != null) {
            assertEquals("Unexpected version code for split: " + apk.getName(),
                    code, apkInfo.getVersionCode());
        }

        if (versionName != null) {
            assertEquals("Unexpected version code for split: " + apk.getName(),
                    versionName, apkInfo.getVersionName());
        }
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param apkFile the apk file
     * @param expectedClassName the class name
     */
    public static boolean checkForClass(
            @NonNull File apkFile,
            @NonNull String expectedClassName)
            throws IOException, LoggedErrorException, InterruptedException {
        // get the dexdump exec
        File dexDump = SdkHelper.getDexDump();

        CommandLineRunner commandLineRunner = new CommandLineRunner(
                new StdLogger(StdLogger.Level.ERROR));
        List<String> command = Lists.newArrayList();

        command.add(dexDump.getAbsolutePath());
        command.add(apkFile.getAbsolutePath());

        List<String> output = runAndGetOutput(commandLineRunner, command);

        for (String line : output) {
            Matcher m = PATTERN.matcher(line.trim());
            if (m.matches()) {
                String className = m.group(1);
                if (expectedClassName.equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Runs a command, and returns the output.
     *
     * @param commandLineRunner the commandline runner
     * @param command the command line args
     *
     * @return the output as a list of files.
     * @throws IOException
     * @throws InterruptedException
     * @throws LoggedErrorException
     */
    @NonNull
    public static List<String> runAndGetOutput(
            @NonNull CommandLineRunner commandLineRunner,
            @NonNull List<String> command)
            throws IOException, InterruptedException, LoggedErrorException {

        final List<String> output = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@com.android.annotations.Nullable String line) {
                if (line != null) {
                    output.add(line);
                }
            }
            @Override
            public void err(@com.android.annotations.Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        return output;
    }
}
