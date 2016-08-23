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
 * Factory for creating STL specification for NDK r13.
 */
public class NdkR13StlSpecificationFactory extends NdkR12StlSpecificationFactory{
    @Override
    @NonNull
    protected List<String> getLibcxxIncludes(@NonNull Abi abi) {
        // With NDK r13, the inner libcxx/libcxxabi directories came out of llvm-libc++ and
        // llvm-libc++abi respectively (side effect of moving to external/libcxx
        // external/libcxxabi).
        return ImmutableList.of(
                "llvm-libc++/include",
                "llvm-libc++abi/include",
                "../android/support/include");
    }
}
