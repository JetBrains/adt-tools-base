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
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer
import org.gradle.nativeplatform.SharedLibraryBinarySpec;

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

    static final Map<String, Collection<String>> STL_SOURCES = [
            "system" : [
                    "system/include"
            ],
            "stlport" : [
                    "stlport/stlport",
                    "gabi++/include",
            ],
            "gnustl" : [
                    "gnu-libstdc++",
                    "gnu-libstdc++/4.6/include",
                    "gnu-libstdc++/4.6/libs/armeabi-v7a/include",
                    "gnu-libstdc++/4.6/include/backward",
            ],
            "gabi++" : [
                    "gabi++",
                    "gabi++/include",
            ],
            "c++" : [
                    "../android/support/include",
                    "llvm-libc++",
                    "../android/compiler-rt",
                    "llvm-libc++/libcxx/include",
                    "gabi++/include",
                    "../android/support/include",
            ],
    ]

    public static File getStlBaseDirectory(NdkHandler ndkHandler) {
        return new File(ndkHandler.getNdkDirectory(), "sources/cxx-stl/");
    }

    public static Collection<String> getStlSources(NdkHandler ndkHandler, String stl) {
        String stlBase = getStlBaseDirectory(ndkHandler);
        String stlName = stl.equals("system") ? "system" : stl.substring(0, stl.indexOf('_'));
        return STL_SOURCES[stlName].collect { String sourceDir ->
            stlBase.toString() + "/" + sourceDir
        }
    }


    public static void checkStl(String stl) {
        if (!VALID_STL.contains(stl)) {
            throw new InvalidUserDataException("Invalid STL: $stl")
        }
    }

    public static void apply(
            NdkHandler ndkHandler,
            String stl,
            TaskContainer tasks,
            File buildDir,
            SharedLibraryBinarySpec binary) {
        StlNativeToolSpecification stlConfig =
                new StlNativeToolSpecification(ndkHandler, stl, binary.targetPlatform)
        stlConfig.apply(binary)

        if (stl.endsWith("_shared")) {
            Task copySharedLib = tasks.create(
                    name: NdkNamingScheme.getTaskName(binary, "copy", "StlSo"),
                    type: Copy) {
                from(stlConfig.getStlLib(binary.targetPlatform.name))
                into(new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)))
            }
            binary.builtBy copySharedLib
        }
    }
}
