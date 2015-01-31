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

package com.android.build.gradle.ndk.internal;

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.StringHelper;
import com.android.builder.model.AndroidProject;
import com.google.common.base.Joiner;

import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;

/**
 * Naming scheme for NdkPlugin's outputs.
 */
public class NdkNamingScheme {

    public static File getObjectFilesOutputDirectory(
            NativeBinarySpec binary,
            File buildDir,
            String sourceSetName) {
        return new File(
                buildDir,
                String.format(
                        "%s/objectFiles/%s/%s",
                        AndroidProject.FD_INTERMEDIATES ,
                        binary.getName(),
                        sourceSetName));
    }

    public static String getTaskName(NativeBinarySpec binary, @Nullable String verb) {
        return getTaskName(binary, verb, null);
    }

    public static String getTaskName(
            NativeBinarySpec binary,
            @Nullable String verb,
            @Nullable String target) {
        StringBuilder sb = new StringBuilder();
        appendCamelCase(sb, verb);
        appendCamelCase(sb, binary.getName());
        appendCamelCase(sb, target);
        return sb.toString();
    }

    private static void appendCamelCase(StringBuilder sb, @Nullable String word) {
        if (word != null) {
            if (sb.length() == 0) {
                sb.append(word);
            } else {
                sb.append(StringHelper.capitalize(word));
            }
        }
    }

    public static String getOutputDirectoryName(NativeBinarySpec binary) {
        return Joiner.on(File.separator).join(
                AndroidProject.FD_INTERMEDIATES,
                "binaries",
                binary.getName(),
                binary.getBuildType().getName(),
                binary.getFlavor().getName(),
                "lib",
                binary.getTargetPlatform().getName());
    }

    public static String getSharedLibraryFileName(String moduleName) {
        return "lib" + moduleName + ".so";
    }
}
