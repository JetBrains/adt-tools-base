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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.nativebinaries.internal.DefaultSharedLibraryBinarySpec;

/**
 * Configuration to setup STL for NDK.
 */
public class StlConfiguration {
    static final String DEFAULT_STL = "system"
    static final String[] VALID_STL = [
            "system",
            "stlport_static",
            "stlport_shared",
            "gnustl_static",
            "gnustl_shared",
            "gabi++_static",
            "gabi++_shared",
            "c++_static",
            "c++_shared",
    ]

    public static void checkStl(String stl) {
        if (!VALID_STL.contains(stl)) {
            throw new InvalidUserDataException("Invalid STL: $stl")
        }
    }

    public static void apply(
            NdkHandler ndkHandler,
            String stl,
            Project project,
            DefaultSharedLibraryBinarySpec binary) {
        StlNativeToolSpecification stlConfig =
                new StlNativeToolSpecification(ndkHandler, stl, binary.targetPlatform)
        stlConfig.apply(binary)

        if (stl.endsWith("_shared")) {
            Task copySharedLib = project.tasks.create(
                    name: binary.namingScheme.getTaskName("copy", "StlSo"),
                    type:Copy) {
                from(stlConfig.getStlLib())
                into(new File(project.buildDir, NdkNamingScheme.getOutputDirectoryName(binary)))
            }
            binary.builtBy copySharedLib
        }
    }
}
