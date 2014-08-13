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

package com.android.build.gradle.ndk.internal

import com.android.SdkConstants
import com.android.build.gradle.ndk.NdkExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.nativebinaries.toolchain.Clang
import org.gradle.nativebinaries.toolchain.Gcc

/**
 * Action to configure toolchain for native binaries.
 */
class ToolchainConfigurationAction implements Action<Project> {

    private static final String[] ABI32 = [
            SdkConstants.ABI_INTEL_ATOM,
            SdkConstants.ABI_ARMEABI_V7A,
            SdkConstants.ABI_ARMEABI,
            SdkConstants.ABI_MIPS]

    private static final String[] ALL_ABI = [
            SdkConstants.ABI_INTEL_ATOM,
            SdkConstants.ABI_INTEL_ATOM64,
            SdkConstants.ABI_ARMEABI_V7A,
            SdkConstants.ABI_ARMEABI,
            SdkConstants.ABI_ARM64_V8A,
            SdkConstants.ABI_MIPS,
            SdkConstants.ABI_MIPS64]

    private static final GCC_PREFIX = [
            (SdkConstants.ABI_INTEL_ATOM) : "i686-linux-android",
            (SdkConstants.ABI_INTEL_ATOM64) : "x86_64-linux-android",
            (SdkConstants.ABI_ARMEABI_V7A) : "arm-linux-androideabi",
            (SdkConstants.ABI_ARMEABI) : "arm-linux-androideabi",
            (SdkConstants.ABI_ARM64_V8A) : "aarch64-linux-android",
            (SdkConstants.ABI_MIPS) : "mipsel-linux-android",
            (SdkConstants.ABI_MIPS64) : "mips64el-linux-android"
    ]

    private NdkBuilder ndkBuilder

    private NdkExtension ndkExtension

    public ToolchainConfigurationAction(NdkBuilder ndkBuilder, NdkExtension ndkExtension) {
        this.ndkBuilder = ndkBuilder
        this.ndkExtension = ndkExtension
    }

    public void execute(Project project) {
        String[] abiList = ndkBuilder.supports64Bits() ? ALL_ABI : ABI32;
        project.model {
            platforms {
                for (String abi: abiList) {
                    "$abi" {
                        // All we care is the name of the platform.  It doesn't matter what the
                        // architecture is, but it must be set to non-x86 so that it does not match
                        // the default supported platform.
                        architecture "ppc"
                        operatingSystem "linux"
                    }
                }
            }
        }

        for (String abi: abiList) {

            // Create toolchain for each ABI.
            configureToolchain(
                    project,
                    ndkExtension.getToolchain(),
                    ndkExtension.getToolchainVersion(),
                    abi)
        }
    }

    /**
     * Configure toolchain for a platform.
     */
    private void configureToolchain(
            Project project,
            String toolchainName,
            String toolchainVersion,
            String platform) {
        String name = "$toolchainName-$toolchainVersion-$platform"
        String bin = (
                ndkBuilder.getToolchainPath(toolchainName, toolchainVersion, platform).toString()
                        + "/bin")

        project.model {
            toolChains {
                "$name"(toolchainName.equals("gcc") ? Gcc : Clang) {
                    target platform

                    if (toolchainName.equals("gcc")) {
                        cCompiler.setExecutable("${GCC_PREFIX[platform]}-gcc")
                        cppCompiler.setExecutable("${GCC_PREFIX[platform]}-g++")
                        linker.setExecutable("${GCC_PREFIX[platform]}-g++")
                        assembler.setExecutable("${GCC_PREFIX[platform]}-as")
                        staticLibArchiver.setExecutable("${GCC_PREFIX[platform]}-ar")
                    }

                    // By default, gradle will use -Xlinker to pass arguments to the linker.
                    // Removing it as it prevents -sysroot from being properly set.
                    linker.withArguments { List<String> args ->
                        args.removeAll("-Xlinker")
                    }
                    path bin
                }
            }
        }
    }

}
