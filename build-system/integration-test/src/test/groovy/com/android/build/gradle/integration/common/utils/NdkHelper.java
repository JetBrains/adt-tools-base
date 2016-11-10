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

package com.android.build.gradle.integration.common.utils;

import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.repository.Revision;
import com.google.common.collect.ImmutableMap;

import java.io.File;

/**
 * Ndk related helper functions.
 */
public class NdkHelper {

    /** Gets the maximum platform version supported by the specified ndk directory. */
    public static int getMaxPlatformSupported(File ndkDir) {
        Revision ndkRevision = NdkHandler.findRevision(ndkDir);
        int major = ndkRevision != null ? ndkRevision.getMajor() : 10;
        // for r10 max platform is 21, r11 max is 24, r12 max platform is 24
        ImmutableMap<Integer, Integer> perVersion = ImmutableMap.of(10, 21, 11, 24, 12, 24, 13, 24);
        return perVersion.get(major);
    }
}
