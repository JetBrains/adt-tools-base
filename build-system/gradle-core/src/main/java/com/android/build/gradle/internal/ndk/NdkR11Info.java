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
import com.android.build.gradle.internal.core.Toolchain;
import com.google.common.base.Objects;

import java.io.File;

/**
 * NdkInfo for r11.
 */
public class NdkR11Info extends DefaultNdkInfo {

    public NdkR11Info(@NonNull File root) {
        super(root);
    }

    @Override
    @NonNull
    public StlNativeToolSpecification getStlNativeToolSpecification(
            @NonNull Stl stl,
            @NonNull String stlVersion,
            @NonNull Abi abi) {
        StlSpecification spec = new NdkR11StlSpecificationFactory().create(
                stl,
                Objects.firstNonNull(
                        stlVersion,
                        getDefaultToolchainVersion(Toolchain.GCC, abi)),
                abi);
        return new DefaultStlNativeToolSpecification(this, spec, stl);
    }
}
