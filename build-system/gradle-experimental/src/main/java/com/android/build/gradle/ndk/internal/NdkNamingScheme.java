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

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.utils.StringHelper.appendCamelCase;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.FileUtils;

import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;

/**
 * Naming scheme for NdkPlugin's outputs.
 */
public class NdkNamingScheme {

    @NonNull
    public static File getObjectFilesOutputDirectory(
            @NonNull NativeBinarySpec binary,
            @NonNull File buildDir,
            @NonNull String sourceSetName) {
        return new File(
                buildDir,
                String.format(
                        "%s/objectFiles/%s/%s",
                        FD_INTERMEDIATES ,
                        binary.getName(),
                        sourceSetName));
    }

    @NonNull
    public static String getTaskName(@NonNull NativeBinarySpec binary, @Nullable String verb) {
        return getTaskName(binary, verb, null);
    }

    @NonNull
    public static String getTaskName(
            @NonNull NativeBinarySpec binary,
            @Nullable String verb,
            @Nullable String target) {
        StringBuilder sb = new StringBuilder();
        appendCamelCase(sb, verb);
        appendCamelCase(sb, binary.getName());
        appendCamelCase(sb, target);
        return sb.toString();
    }

    @NonNull
    public static String getNdkBuildTaskName(@NonNull NativeBinarySpec binary) {
        return getTaskName(binary, "ndkBuild");
    }

    /**
     * Return the name of the directory that will contain the final output of the native binary.
     */
    @NonNull
    public static String getOutputDirectoryName(
            @NonNull String buildType,
            @NonNull String productFlavor,
            @NonNull String abi) {
        return FileUtils.join(
                FD_INTERMEDIATES,
                "binaries",
                buildType,
                productFlavor,
                "lib",
                abi);
    }


    @NonNull
    public static String getStandaloneOutputDirectoryName(@NonNull NativeBinarySpec binary) {
        return FileUtils.join(
                FD_OUTPUTS,
                "native",
                binary.getBuildType().getName(),
                binary.getFlavor().getName(),
                "lib",
                binary.getTargetPlatform().getName());
    }

    @NonNull
    public static String getOutputDirectoryName(@NonNull NativeBinarySpec binary) {
        return getOutputDirectoryName(
                binary.getBuildType().getName(),
                binary.getFlavor().getName(),
                binary.getTargetPlatform().getName());
    }

    /**
     * Return the name of the directory that will contain the native library with debug symbols.
     */
    @NonNull
    public static String getDebugLibraryDirectoryName(
            @NonNull String buildType,
            @NonNull String productFlavor,
            @NonNull String abi) {
        return FileUtils.join(
                FD_INTERMEDIATES,
                "binaries",
                buildType,
                productFlavor,
                "obj",
                abi);
    }

    @NonNull
    public static String getDebugLibraryDirectoryName(@NonNull NativeBinarySpec binary) {
        return getDebugLibraryDirectoryName(
                binary.getBuildType().getName(),
                binary.getFlavor().getName(),
                binary.getTargetPlatform().getName());
    }

    /**
     * Return the name of the output shared library.
     */
    @NonNull
    public static String getSharedLibraryFileName(@NonNull String moduleName) {
        return "lib" + moduleName + ".so";
    }

    public static String getStaticLibraryFileName(String moduleName) {
        return "lib" + moduleName + ".a";
    }
}
