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
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.platform.NativePlatform

/**
 * Flag configuration for GCC toolchain.
 */

class GccNativeToolSpecification extends AbstractNativeToolSpecification {

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
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fno-strict-aliasing",
                    "-finline-limit=64",
            ],
            (SdkConstants.ABI_ARMEABI_V7A) : [
                    "-fpic",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fstack-protector",
                    "-no-canonical-prefixes",
                    "-march=armv7-a",
                    "-mfpu=vfpv3-d16",
                    "-mfloat-abi=softfp",
                    "-mthumb",
                    "-Os",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fno-strict-aliasing",
                    "-finline-limit=64",
            ],
            (SdkConstants.ABI_ARM64_V8A) : [
                    "-fpic",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fstack-protector",
                    "-no-canonical-prefixes",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fstrict-aliasing",
                    "-funswitch-loops",
                    "-finline-limit=300",
            ],
            (SdkConstants.ABI_INTEL_ATOM) : [
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-no-canonical-prefixes",
                    "-fstack-protector",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fstrict-aliasing",
                    "-funswitch-loops",
                    "-finline-limit=300",
            ],
            (SdkConstants.ABI_INTEL_ATOM64) : [
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-no-canonical-prefixes",
                    "-fstack-protector",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-fstrict-aliasing",
                    "-funswitch-loops",
                    "-finline-limit=300",
            ],
            (SdkConstants.ABI_MIPS)       : [
                    "-fpic",
                    "-fno-strict-aliasing",
                    "-finline-functions",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fmessage-length=0",
                    "-fno-inline-functions-called-once",
                    "-fgcse-after-reload",
                    "-frerun-cse-after-loop",
                    "-frename-registers",
                    "-no-canonical-prefixes",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-funswitch-loops",
                    "-finline-limit=300",
            ],
            (SdkConstants.ABI_MIPS64) : [
                    "-fpic",
                    "-fno-strict-aliasing",
                    "-finline-functions",
                    "-ffunction-sections",
                    "-funwind-tables",
                    "-fmessage-length=0",
                    "-fno-inline-functions-called-once",
                    "-fgcse-after-reload",
                    "-frerun-cse-after-loop",
                    "-frename-registers",
                    "-no-canonical-prefixes",
                    "-O2",
                    "-g",
                    "-DNDEBUG",
                    "-fomit-frame-pointer",
                    "-funswitch-loops",
                    "-finline-limit=300",
            ],
    ]

    private static final def DEBUG_CFLAGS = [
            (SdkConstants.ABI_ARMEABI) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_ARMEABI_V7A) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_ARM64_V8A) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_INTEL_ATOM) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_INTEL_ATOM64) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-strict-aliasing",
            ],
            (SdkConstants.ABI_MIPS) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
                    "-fno-unswitch-loops",
            ],
            (SdkConstants.ABI_MIPS64) : [
                    "-O0",
                    "-UNDEBUG",
                    "-fno-omit-frame-pointer",
            ]
    ]

    private static final Iterable<String> LDFLAGS = [
            "-no-canonical-prefixes",
    ]

    private NativePlatform platform

    private boolean isDebugBuild

    GccNativeToolSpecification(BuildType buildType, NativePlatform platform) {
        this.isDebugBuild = (buildType.name.equals(BuilderConstants.DEBUG))
        this.platform = platform
    }

    @Override
    public Iterable<String> getCFlags() {
        RELEASE_CFLAGS[platform.name] + (isDebugBuild ? DEBUG_CFLAGS[platform.name] : [])
    }

    @Override
    public Iterable<String> getCppFlags() {
        getCFlags()
    }

    @Override
    public Iterable<String> getLdFlags() {
        LDFLAGS
    }
}
