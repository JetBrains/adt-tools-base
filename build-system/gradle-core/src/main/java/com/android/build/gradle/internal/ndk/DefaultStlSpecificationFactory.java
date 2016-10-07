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
import com.android.build.gradle.internal.core.Abi;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default factory for creating STL specification for NDK r10.
 */
public class DefaultStlSpecificationFactory implements StlSpecificationFactory {

    @Override
    @NonNull
    public StlSpecification create(@NonNull Stl stl, @NonNull String stlVersion, @NonNull Abi abi) {
        switch (stl) {
            case SYSTEM:
                return createSpec(
                        abi,
                        ImmutableList.of("system/include"),
                        "",
                        ImmutableList.of(),
                        ImmutableList.of());
            case STLPORT_SHARED:
                return createSpec(
                        abi,
                        ImmutableList.of("stlport/stlport", "gabi++/include"),
                        "stlport",
                        ImmutableList.of(),
                        ImmutableList.of("libstlport_shared.so"));
            case STLPORT_STATIC:
                return createSpec(
                        abi,
                        ImmutableList.of("stlport/stlport", "../gabi++/include"),
                        "stlport",
                        ImmutableList.of("libstlport_static.a"),
                        ImmutableList.of());
            case GNUSTL_SHARED:
                return createSpec(
                        abi,
                        ImmutableList.of(
                                "gnu-libstdc++/" + stlVersion + "/include",
                                "gnu-libstdc++/" + stlVersion + "/libs/" + abi.getName() + "/include",
                                "gnu-libstdc++/" + stlVersion + "/include/backward"),
                        "gnu-libstdc++/" + stlVersion,
                        ImmutableList.of(),
                        ImmutableList.of("libgnustl_shared.so"));
            case GNUSTL_STATIC:
                return createSpec(
                        abi,
                        ImmutableList.of(
                                "gnu-libstdc++/" + stlVersion + "/include",
                                "gnu-libstdc++/" + stlVersion + "/libs/" + abi.getName() + "/include",
                                "gnu-libstdc++/" + stlVersion + "/include/backward"),
                        "gnu-libstdc++/" + stlVersion,
                        ImmutableList.of("libgnustl_static.a"),
                        ImmutableList.of());
            case GABIPP_SHARED:
                return createSpec(
                        abi,
                        ImmutableList.of("gabi++/include"),
                        "gabi++",
                        ImmutableList.of(),
                        ImmutableList.of("libgabi++_shared.so"));
            case GABIPP_STATIC:
                return createSpec(
                        abi,
                        ImmutableList.of("gabi++/include"),
                        "gabi++",
                        ImmutableList.of("libgabi++_static.a"),
                        ImmutableList.of());
            case CPP_SHARED:
                return createSpec(
                        abi,
                        getLibcxxIncludes(abi),
                        "llvm-libc++",
                        getLibcxxStaticLibs(abi, /* staticStl = */ false),
                        ImmutableList.of("libc++_shared.so"));
            case CPP_STATIC:
                return createSpec(
                        abi,
                        getLibcxxIncludes(abi),
                        "llvm-libc++",
                        getLibcxxStaticLibs(abi, /* staticStl = */ true),
                        ImmutableList.of());
            default:
                throw new RuntimeException("Unreachable.  Unknown STL: " + stl + ".");
        }
    }

    protected static StlSpecification createSpec(
            @NonNull Abi abi,
            @NonNull List<String> includes,
            @NonNull String libPath,
            @NonNull List<String> staticLibs,
            @NonNull List<String> sharedLibs) {
        String base = "sources/cxx-stl";
        return new StlSpecification(
                includes.stream()
                        .map(include -> FileUtils.join(base, include))
                        .collect(Collectors.toList()),
                staticLibs.stream()
                        .map(lib -> FileUtils.join(base, libPath, "libs", abi.getName(), lib))
                        .collect(Collectors.toList()),
                sharedLibs.stream()
                        .map(lib -> FileUtils.join(base, libPath, "libs", abi.getName(), lib))
                        .collect(Collectors.toList()));
    }

    @NonNull
    protected List<String> getLibcxxIncludes(@NonNull Abi abi) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("llvm-libc++/libcxx/include");

        // The libc++abi in NDK r10 had issues with non-ARM architectures, so it uses gabi++ for
        // those.
        if (abi == Abi.ARMEABI || abi == Abi.ARMEABI_V7A || abi == Abi.ARM64_V8A) {
            builder.add("llvm-libc++abi/libcxxabi/include");
        } else {
            builder.add("gabi++/include");
        }

        builder.add("../android/support/include");

        return builder.build();
    }

    @NonNull
    protected List<String> getLibcxxStaticLibs(@NonNull Abi abi, boolean staticStl) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (staticStl) {
            builder.add("libc++_static.a");
        }

        // This is nearly always needed for ARM5, so just link it by default.
        if (abi == Abi.ARMEABI) {
            builder.add("libatomic.a");
        }

        return builder.build();
    }
}
