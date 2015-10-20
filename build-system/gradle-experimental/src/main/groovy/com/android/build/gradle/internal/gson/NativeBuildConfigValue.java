/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.gson;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.managed.NativeToolchain;

import org.gradle.api.Action;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Value type for {@link NativeBuildConfig} to be used with Gson.
 */
public class NativeBuildConfigValue {

    @Nullable
    Collection<File> buildFiles;
    @Nullable
    Collection<String> generatorCommand;
    @Nullable
    Map<String, NativeLibraryValue> libraries;
    @Nullable
    Map<String, NativeToolchainValue> toolchains;

    public void copyTo(@NonNull NativeBuildConfig config) {
        if (buildFiles != null) {
            config.getBuildFiles().clear();
            config.getBuildFiles().addAll(buildFiles);
        }
        if (generatorCommand != null) {
            config.getGeneratorCommand().clear();
            config.getGeneratorCommand().addAll(generatorCommand);
        }
        if (libraries != null) {
            for (final Map.Entry<String, NativeLibraryValue> entry : libraries.entrySet()) {
                config.getLibraries().create(entry.getKey(), new Action<NativeLibrary>() {
                    @Override
                    public void execute(NativeLibrary nativeLibrary) {
                        entry.getValue().copyTo(nativeLibrary);
                    }
                });
            }
        }
        if (toolchains != null) {
            for (final Map.Entry<String, NativeToolchainValue> entry : toolchains.entrySet()) {
                config.getToolchains().create(entry.getKey(), new Action<NativeToolchain>() {
                    @Override
                    public void execute(NativeToolchain nativeToolchain) {
                        entry.getValue().copyTo(nativeToolchain);
                    }
                });
            }
        }
    }
}
