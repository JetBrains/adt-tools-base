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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.NativeSourceFileValue;
import com.android.build.gradle.external.gson.NativeSourceFolderValue;
import com.android.build.gradle.external.gson.NativeToolchainValue;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.managed.NativeSourceFile;
import com.android.build.gradle.managed.NativeSourceFolder;
import com.android.build.gradle.managed.NativeToolchain;

import org.gradle.api.Action;

import java.util.Map;

/**
 * Methods for converting Native*Value GSon structures to Native* gradle model structures.
 */
public class NativeBuildConfigGsonUtil {

    /**
     * Copies this NativeBuildConfigValue to another.
     *
     * If target already contains non-empty lists, values are appended.
     */
    public static void copyToNativeBuildConfig(
            @NonNull NativeBuildConfigValue value,
            @NonNull NativeBuildConfig config) {
        if (value.buildFiles != null) {
            config.getBuildFiles().addAll(value.buildFiles);
        }
        if (value.cleanCommandStrings != null) {
            config.getCleanCommandStrings().addAll(value.cleanCommandStrings);
        }
        if (value.cleanCommands != null) {
            config.getCleanCommands().addAll(value.cleanCommands);
            config.getCleanCommands().add(null);
        }
        if (value.libraries != null) {
            for (final Map.Entry<String, NativeLibraryValue> entry : value.libraries.entrySet()) {
                config.getLibraries().create(entry.getKey(), new Action<NativeLibrary>() {
                    @Override
                    public void execute(NativeLibrary nativeLibrary) {
                        copyToNativeLibrary(entry.getValue(), nativeLibrary);
                    }
                });
            }
        }
        if (value.toolchains != null) {
            for (final Map.Entry<String, NativeToolchainValue> entry : value.toolchains.entrySet()) {
                config.getToolchains().create(entry.getKey(), new Action<NativeToolchain>() {
                    @Override
                    public void execute(NativeToolchain nativeToolchain) {
                        copyToNativeToolchain(entry.getValue(), nativeToolchain);
                    }
                });
            }
        }
        if (value.cFileExtensions != null) {
            config.getCFileExtensions().addAll(value.cFileExtensions);
        }
        if (value.cppFileExtensions != null) {
            config.getCppFileExtensions().addAll(value.cppFileExtensions);
        }
    }

    private static void copyToNativeSourceFolder(
            @NonNull NativeSourceFolderValue value,
            @NonNull NativeSourceFolder folder) {
        folder.setSrc(value.src);
        if (value.cFlags != null) {
            folder.getCFlags().clear();
            folder.getCFlags().addAll(value.cFlags);
        }
        if (value.cppFlags != null) {
            folder.getCppFlags().clear();
            folder.getCppFlags().addAll(value.cppFlags);
        }
        folder.setWorkingDirectory(value.workingDirectory);
    }

    private static void copyToNativeSourceFile(
            @NonNull NativeSourceFileValue value,
            @NonNull NativeSourceFile file) {
        file.setSrc(value.src);
        if (value.flags != null) {
            file.getFlags().clear();
            file.getFlags().addAll(value.flags);
        }
        file.setWorkingDirectory(value.workingDirectory);
    }

    private static void copyToNativeLibrary(
            @NonNull NativeLibraryValue value,
            @NonNull NativeLibrary library) {
        library.setBuildCommandString(value.buildCommandString);
        if (value.buildCommand != null) {
            library.getBuildCommand().clear();
            library.getBuildCommand().addAll(value.buildCommand);
        }
        library.setToolchain(value.toolchain);
        if (value.folders != null) {
            for (final NativeSourceFolderValue folder : value.folders) {
                library.getFolders().create(new Action<NativeSourceFolder>() {
                    @Override
                    public void execute(NativeSourceFolder nativeSourceFolder) {
                        copyToNativeSourceFolder(folder, nativeSourceFolder);
                    }
                });
            }
        }

        library.setGroupName(value.groupName);
        library.setAbi(value.abi);

        if (value.files != null) {
            for (final NativeSourceFileValue folder : value.files) {
                library.getFiles().create(new Action<NativeSourceFile>() {
                    @Override
                    public void execute(NativeSourceFile nativeSourceFolder) {
                        copyToNativeSourceFile(folder, nativeSourceFolder);
                    }
                });
            }
        }
        if (value.exportedHeaders != null) {
            library.getExportedHeaders().addAll(value.exportedHeaders);
        }
        library.setOutput(value.output);
    }

    private static void copyToNativeToolchain(
            @NonNull NativeToolchainValue value,
            @NonNull NativeToolchain toolchain) {
        toolchain.setCCompilerExecutable(value.cCompilerExecutable);
        toolchain.setCppCompilerExecutable(value.cppCompilerExecutable);
    }
}
