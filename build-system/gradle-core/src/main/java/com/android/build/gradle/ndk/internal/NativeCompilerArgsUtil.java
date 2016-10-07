/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.ndk.internal;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility to transform compiler arguments as they would when Gradle insert them into option.txt.
 *
 * Follow the format conversion use in org.gradle.internal.process.ArgWriter.
 */
public final class NativeCompilerArgsUtil {

    private NativeCompilerArgsUtil() {}

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    /**
     * Convert compile arguments to the form used in options.txt.
     */
    @NonNull
    public static List<String> transform(@NonNull Iterable<String> args) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String arg : args) {
            String str = arg;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"");
            if (WHITESPACE.matcher(str).find()) {
                str = '\"' + str + '\"';
            }
            builder.add(str);
        }
        return builder.build();
    }
}
