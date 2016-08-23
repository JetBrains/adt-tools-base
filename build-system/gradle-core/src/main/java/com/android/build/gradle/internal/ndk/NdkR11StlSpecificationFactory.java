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
 * Factory for creating STL specification for NDK r11.
 */
public class NdkR11StlSpecificationFactory extends DefaultStlSpecificationFactory{
    @Override
    @NonNull
    public StlSpecification create(@NonNull Stl stl, @NonNull String stlVersion, @NonNull Abi abi) {
        switch (stl) {
            case SYSTEM:
            case GNUSTL_SHARED:
            case GNUSTL_STATIC:
            case STLPORT_SHARED:
            case STLPORT_STATIC:
            case CPP_SHARED:
            case CPP_STATIC:
                return super.create(stl, stlVersion, abi);
            case GABIPP_SHARED:
            case GABIPP_STATIC:
                throw new RuntimeException("gabi++ is not available beginning with NDK r11.");
            default:
                throw new RuntimeException("Unreachable.  Unknown STL: " + stl + ".");
        }
    }

    @Override
    @NonNull
    protected List<String> getLibcxxIncludes(@NonNull Abi abi) {
        // With NDK r11, all ABIs switched to libc++abi.
        return ImmutableList.of(
                "llvm-libc++/libcxx/include",
                "llvm-libc++abi/libcxxabi/include",
                "../android/support/include");
    }
}
