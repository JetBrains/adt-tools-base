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
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Factory for creating STL specification for NDK r12.
 */
public class NdkR12StlSpecificationFactory extends DefaultStlSpecificationFactory{
    @Override
    @NonNull
    public StlSpecification create(@NonNull Stl stl, @NonNull String stlVersion, @NonNull Abi abi) {
        switch (stl) {
            case SYSTEM:
            case GNUSTL_SHARED:
            case GNUSTL_STATIC:
            case STLPORT_SHARED:
            case STLPORT_STATIC:
                return super.create(stl, stlVersion, abi);
            case CPP_SHARED:
                List<String> staticLibs = (abi == Abi.ARMEABI || abi == Abi.ARMEABI_V7A)
                        ? ImmutableList.of("libunwind.a")
                        : ImmutableList.of();
                return createSpec(
                        abi,
                        ImmutableList.of(
                                "llvm-libc++/libcxx/include",
                                "../android/support/include"),
                        "llvm-libc++",
                        staticLibs,
                        ImmutableList.of("libc++_shared.so"));
            case CPP_STATIC:
                ImmutableList.Builder<String> builder = ImmutableList.builder();
                builder.add("libc++_static.a");
                builder.add("libc++abi.a");
                if (abi == Abi.ARMEABI || abi == Abi.ARMEABI_V7A) {
                    builder.add("libunwind.a");
                }
                builder.add("libandroid_support.a");
                if (abi == Abi.ARMEABI) {
                    builder.add("libatomic.a");
                }
                return createSpec(
                        abi,
                        ImmutableList.of(
                                "llvm-libc++/libcxx/include",
                                "../android/support/include"),
                        "llvm-libc++",
                        builder.build(),
                        ImmutableList.of());
            case GABIPP_SHARED:
            case GABIPP_STATIC:
                throw new RuntimeException("gabi++ is not availabe in NDK r12.");
            default:
                throw new RuntimeException("Unreachable.  Unknown STL: " + stl + ".");
        }
    }

}
