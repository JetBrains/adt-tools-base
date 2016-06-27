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

package com.android.build.gradle.internal.ndk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.Toolchain;

import java.io.File;

/**
 * Interface describing the NDK.
 */
public interface NdkInfo {

    /**
     * Return the directory of the NDK.
     */
    @NonNull
    File getRootDirectory();

    /**
     * Returns the sysroot directory for the toolchain.
     */
    @NonNull
    String getSysrootPath(@NonNull Abi abi, @NonNull String platformVersion);

    /**
     * Retrieve the newest supported version if it is not the specified version is not supported.
     *
     * An older NDK may not support the specified compiledSdkVersion.  In that case, determine what
     * is the newest supported version and modify compileSdkVersion.
     */
    @Nullable
    String findLatestPlatformVersion(@NonNull String targetPlatformString);

    int findSuitablePlatformVersion(String abi, int minSdkVersion);

    /**
     * Return the executable for compiling C code.
     */
    @NonNull
    File getCCompiler(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi);

    /**
     * Return the executable for compiling C++ code.
     */
    @NonNull
    File getCppCompiler(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi);

    @NonNull
    File getAr(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi);

    /**
     * Return the executable for removing debug symbols from a shared object.
     */
    @NonNull
    File getStripExecutable(Toolchain toolchain, String toolchainVersion, Abi abi);

    @NonNull
    StlNativeToolSpecification getStlNativeToolSpecification(
            @NonNull Stl stl,
            @Nullable String stlVersion,
            @NonNull Abi abi);

    /**
     * Return the directory containing the toolchain.
     *
     * @param toolchain toolchain to use.
     * @param toolchainVersion toolchain version to use.
     * @param abi target ABI of the toolchaina
     * @return a directory that contains the executables.
     */
    @NonNull
    File getToolchainPath(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi);

    /**
     * Return the default version of the specified toolchain for a target abi.
     *
     * The default version is the highest version found in the NDK for the specified toolchain and
     * ABI.  The result is cached for performance.
     */
    @NonNull
    String getDefaultToolchainVersion(@NonNull Toolchain toolchain, @NonNull final Abi abi);
}
