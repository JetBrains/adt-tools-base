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
import com.android.builder.core.BuilderConstants
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.platform.Platform

/**
 * Flag configuration for Clang toolchain.
 */
class ClangNativeToolSpecification extends AbstractNativeToolSpecification {

    private NdkBuilder ndkBuilder

    private Platform platform

    private boolean isDebugBuild

    private static final def TARGET_TRIPLE = [
            (SdkConstants.ABI_INTEL_ATOM) : "i686-none-linux-android",
            (SdkConstants.ABI_ARMEABI_V7A) : "armv7-none-linux-android",
            (SdkConstants.ABI_ARMEABI) : "armv5-none-linux-android",
            (SdkConstants.ABI_MIPS) : "mipsel-none-linux-android",
    ]

    private static final def RELEASE_CFLAGS = [
            (SdkConstants.ABI_ARMEABI) : [
                    "-fpic",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fstack-protector",
                    "-no-canonical-prefixes",
                    "-march=armv5te",
                    "-mtune=xscale",
                    "-msoft-float",
                    "-mthumb",
                    "-Os",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_ARMEABI_V7A) : [
                    "-fpic",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fstack-protector",
                    "-no-canonical-prefixes",
                    "-march=armv7-a",
                    "-mfloat-abi=softfp",
                    "-mfpu=vfpv3-d16",
                    "-mthumb",
                    "-Os",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_INTEL_ATOM) : [
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fstack-protector",
                    "-fPIC",
                    "-no-canonical-prefixes",
                    "-O2",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fstrict-aliasing",
            ],
            (SdkConstants.ABI_MIPS) : [
                    "-fpic",
                    "-fno-strict-aliasing",
                    "-finline-functions",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fmessage-length=0",
                    "-no-canonical-prefixes",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
            ]
    ]

    private static final def DEBUG_CFLAGS = [
            (SdkConstants.ABI_ARMEABI) : [
                    "-O0",
                    "-UNDEBUG",
                    "-marm",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_ARMEABI_V7A) : [
                    "-O0",
                    "-UNDEBUG",
                    "-marm",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_INTEL_ATOM) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fomit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_MIPS) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fomit-frame-pointer",
            ]
    ]

    public ClangNativeToolSpecification(
            NdkBuilder ndkBuilder,
            BuildType buildType,
            Platform platform) {
        this.ndkBuilder = ndkBuilder
        this.isDebugBuild = (buildType.name.equals(BuilderConstants.DEBUG))
        this.platform = platform
    }

    @Override
    public Iterable<String> getCFlags() {
        getTargetFlags() + RELEASE_CFLAGS[platform.name] + DEBUG_CFLAGS[platform.name]
    }

    @Override
    public Iterable<String> getCppFlags() {
        getCFlags()
    }

    @Override
    public Iterable<String> getLdFlags() {
        getTargetFlags() +
                (platform.name.equals(SdkConstants.ABI_ARMEABI_V7A) ? ["-Wl,--fix-cortex-a8"] : [])
    }

    private Iterable<String> getTargetFlags() {
        [
                "-gcc-toolchain",
                ndkBuilder.getToolchainPath("gcc", "4.8", platform.name),
                "-target",
                TARGET_TRIPLE[platform.name]
        ]
    }
}
