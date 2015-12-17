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
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.managed.NativeSourceFile;
import com.android.build.gradle.managed.NativeSourceFolder;

import org.gradle.api.Action;

import java.io.File;
import java.util.Collection;

/**
 * Value type of {@link NativeLibrary} to be used with Gson.
 */
public class NativeLibraryValue {
    @Nullable
    String executable;
    @Nullable
    Collection<String> args;
    @Nullable
    String toolchain;
    @Nullable
    Collection<NativeSourceFolderValue> folders;
    @Nullable
    Collection<NativeSourceFileValue> files;
    @Nullable
    File output;

    void copyTo(@NonNull NativeLibrary library) {
        library.setExecutable(executable);
        if (args != null) {
            library.getArgs().clear();
            library.getArgs().addAll(args);
        }
        library.setToolchain(toolchain);
        if (folders != null) {
            for (final NativeSourceFolderValue folder : folders) {
                library.getFolders().create(new Action<NativeSourceFolder>() {
                    @Override
                    public void execute(NativeSourceFolder nativeSourceFolder) {
                        folder.copyTo(nativeSourceFolder);
                    }
                });
            }
        }
        if (files != null) {
            for (final NativeSourceFileValue folder : files) {
                library.getFiles().create(new Action<NativeSourceFile>() {
                    @Override
                    public void execute(NativeSourceFile nativeSourceFolder) {
                        folder.copyTo(nativeSourceFolder);
                    }
                });
            }
        }
        library.setOutput(output);
    }

}
