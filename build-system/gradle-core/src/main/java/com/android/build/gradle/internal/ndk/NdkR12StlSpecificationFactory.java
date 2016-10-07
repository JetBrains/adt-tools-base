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
public class NdkR12StlSpecificationFactory extends NdkR11StlSpecificationFactory{
    @Override
    @NonNull
    protected List<String> getLibcxxStaticLibs(@NonNull Abi abi, boolean staticStl) {
        // NDK r12 moved libunwind and libandroid_support out of libc++ itself (avoids ODR issues
        // that were causing exception unwinding to fail). We now have to link these libraries
        // manually.
        // TODO: Investigate using the linker scripts?
        // The NDK ships libc++.a and libc++.so that are linker scripts that automatically add
        // -lc++abi and friends, but that requires that those libraries are already on the link
        // path. It seems that we're using the full paths to the libraries here, so that won't work.
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (staticStl) {
            builder.add("libc++_static.a");
            builder.add("libc++abi.a");
        }

        builder.add("libandroid_support.a");

        if (abi == Abi.ARMEABI || abi == Abi.ARMEABI_V7A) {
            builder.add("libunwind.a");
        }

        // This is nearly always needed for ARM5, so just link it by default.
        if (abi == Abi.ARMEABI) {
            builder.add("libatomic.a");
        }

        return builder.build();
    }
}
