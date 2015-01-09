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
import com.android.ide.common.process.CachedProcessOutputHandler;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StdLogger;
import com.google.common.base.Splitter;

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
            throws IOException, ProcessException {
        checkVersion(apk, code, null /* versionName */);
    }

    public static void checkVersionName(
        @NonNull File apk,
        @Nullable String name)
        throws IOException, ProcessException {
        checkVersion(apk, null, name);
    }

    public static void checkVersion(
            @NonNull File apk,
            @Nullable Integer code,
            @Nullable String versionName)
            throws IOException, ProcessException {
        ProcessExecutor processExecutor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));

        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), processExecutor);

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
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     */
    public static boolean checkForClass(
            @NonNull File apkFile,
            @NonNull String expectedClassName)
            throws ProcessException, IOException {
        // get the dexdump exec
        File dexDump = SdkHelper.getDexDump();

        ProcessExecutor executor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(dexDump);
        builder.addArgs(apkFile.getAbsolutePath());

        List<String> output = runAndGetOutput(builder.createProcess(), executor);

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

    @NonNull
    public static List<String> getApkBadging(@NonNull File apk) throws ProcessException {
        File aapt = SdkHelper.getAapt();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(aapt);
        builder.addArgs("dump", "badging", apk.getPath());

        return runAndGetOutput(builder.createProcess());
    }

    /**
     * Runs a process, and returns the output.
     *
     * @param processInfo the process info to run
     *
     * @return the output as a list of files.
     * @throws ProcessException
     */
    @NonNull
    public static List<String> runAndGetOutput(@NonNull ProcessInfo processInfo)
            throws ProcessException {

        ProcessExecutor executor = new DefaultProcessExecutor(
                new StdLogger(StdLogger.Level.ERROR));
        return runAndGetOutput(processInfo, executor);
    }

    /**
     * Runs a process, and returns the output.
     *
     * @param processInfo the process info to run
     * @param processExecutor the process executor
     *
     * @return the output as a list of files.
     * @throws ProcessException
     */
    @NonNull
    public static List<String> runAndGetOutput(
            @NonNull ProcessInfo processInfo,
            @NonNull ProcessExecutor processExecutor)
            throws ProcessException {
        CachedProcessOutputHandler handler = new CachedProcessOutputHandler();
        processExecutor.execute(processInfo, handler).rethrowFailure().assertNormalExitValue();
        return Splitter.on('\n').splitToList(handler.getProcessOutput().getStandardOutputAsString());
    }
}
