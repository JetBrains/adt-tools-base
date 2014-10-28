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
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelType
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.platform.base.PlatformContainer
import org.gradle.platform.base.ToolChainRegistry

/**
 * Action to configure toolchain for native binaries.
 */
class ToolchainConfigurationAction implements Action<Project> {

    private static final GCC_PREFIX = [
            (SdkConstants.ABI_INTEL_ATOM) : "i686-linux-android",
            (SdkConstants.ABI_INTEL_ATOM64) : "x86_64-linux-android",
            (SdkConstants.ABI_ARMEABI_V7A) : "arm-linux-androideabi",
            (SdkConstants.ABI_ARMEABI) : "arm-linux-androideabi",
            (SdkConstants.ABI_ARM64_V8A) : "aarch64-linux-android",
            (SdkConstants.ABI_MIPS) : "mipsel-linux-android",
            (SdkConstants.ABI_MIPS64) : "mips64el-linux-android"
    ]

    private NdkHandler ndkHandler

    private NdkExtension ndkExtension

    public ToolchainConfigurationAction(NdkHandler ndkHandler, NdkExtension ndkExtension) {
        this.ndkHandler = ndkHandler
        this.ndkExtension = ndkExtension
    }

    public void execute(Project project) {
        // Create android platforms.
        configurePlatforms(
                project.modelRegistry.get(ModelPath.path("platforms"), ModelType.of(PlatformContainer)),
                ndkHandler)

        // Create toolchain for each ABI.
        configureToolchain(
                project.modelRegistry.get(ModelPath.path("toolChains"), ModelType.of(ToolChainRegistry)),
                ndkExtension.getToolchain(),
                ndkExtension.getToolchainVersion(),
                ndkHandler)
    }

    public static void configurePlatforms(PlatformContainer platforms, NdkHandler ndkHandler) {
        List<String> abiList = ndkHandler.getSupportedAbis();
        for (String abi : abiList) {
            NativePlatform platform = platforms.maybeCreate(abi, NativePlatform)

            // All we care is the name of the platform.  It doesn't matter what the
            // architecture is, but it must be set to non-x86 so that it does not match
            // the default supported platform.
            platform.architecture "ppc"
            platform.operatingSystem "linux"
        }
    }

    /**
     * Configure toolchain for a platform.
     */
    public static void configureToolchain(
            NativeToolChainRegistry toolchains,
            String toolchainName,
            String toolchainVersion,
            NdkHandler ndkHandler) {
        toolchains.create("ndk-" + toolchainName, toolchainName.equals("gcc") ? Gcc : Clang) {
            // Configure each platform.
            List<String> abiList = ndkHandler.getSupportedAbis();
            for (String abi: abiList) {
                String platform = abi

                target(platform) {
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

                    String bin = (
                            ndkHandler.getToolchainPath(toolchainName, toolchainVersion, platform).
                                    toString()
                                    + "/bin")
                    path bin
                }
            }
        }
    }
}
