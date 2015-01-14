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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.CachedProcessOutputHandler;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.StdLogger;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper to help read/test the content of generated apk file.
 */
public class ApkHelper {

    private static final Pattern PATTERN_LOCALES = Pattern.compile(
            "^locales\\W*:\\W*(.+)$");

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

    @NonNull
    public static List<String> getApkBadging(@NonNull File apk) throws ProcessException {
        File aapt = SdkHelper.getAapt();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(aapt);
        builder.addArgs("dump", "badging", apk.getPath());

        return ApkHelper.runAndGetOutput(builder.createProcess());
    }

    /**
     * Returns the locales of an apk as found in the badging information
     * @param apk the apk
     * @return the list of locales or null.
     * @throws ProcessException
     *
     * @see #getApkBadging(File)
     */
    @Nullable
    public static List<String> getLocales(@NonNull File apk) throws ProcessException {
        List<String> output = getApkBadging(apk);

        for (String line : output) {
            Matcher m = PATTERN_LOCALES.matcher(line.trim());
            if (m.matches()) {
                List<String> list = Splitter.on(' ').splitToList(m.group(1).trim());
                List<String> result = Lists.newArrayListWithCapacity(list.size());
                for (String local: list) {
                    // remove the '' on each side.
                    result.add(local.substring(1, local.length() - 1));
                }

                return result;
            }
        }

        return null;
    }
}
