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
import com.android.build.gradle.managed.NativeSourceFolder;

import org.gradle.model.Unmanaged;

import java.io.File;
import java.util.List;

/**
 * Value type for {@link NativeSourceFolder} to be used with Gson.
 */
public class NativeSourceFolderValue {
    @Nullable
    File src;
    @Nullable
    List<String> cFlags;
    @Nullable
    List<String> cppFlags;

    void copyTo(@NonNull NativeSourceFolder folder) {
        folder.setSrc(src);
        if (cFlags != null) {
            folder.getCFlags().clear();
            folder.getCFlags().addAll(cFlags);
        }
        if (cppFlags != null) {
            folder.getCppFlags().clear();
            folder.getCppFlags().addAll(cppFlags);
        }
    }
}
