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

import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.platform.NativePlatform

/**
 * Factory to create a NativeToolSpecification.
 */
class NativeToolSpecificationFactory {
    /**
     * Returns a NativeToolSpecification.
     *
     * @param buildType Build type of the native binary.
     * @param platform Target platform of the native binary.
     * @return A NativeToolSpecification for the targeted native binary.
     */
    public static NativeToolSpecification create(
            NdkHandler ndkHandler,
            BuildType buildType,
            NativePlatform platform) {
        String toolchain = ndkHandler.getNdkExtension().getToolchain()
        return (toolchain == null || toolchain.equals("gcc")
                ? new GccNativeToolSpecification(buildType, platform)
                : new ClangNativeToolSpecification(ndkHandler, buildType, platform))
    }
}
