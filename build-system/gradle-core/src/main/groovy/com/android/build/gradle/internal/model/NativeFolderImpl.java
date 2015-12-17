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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeFolder;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

/**
 * Implementation of {@link NativeFolder}
 */
public class NativeFolderImpl implements NativeFolder, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final File folderPath;
    @NonNull
    private final Map<String, String> perLanguageSettings;

    public NativeFolderImpl(
            @NonNull File folderPath,
            @NonNull Map<String, String> perLanguageSettings) {
        this.folderPath = folderPath;
        this.perLanguageSettings = perLanguageSettings;
    }

    @Override
    @NonNull
    public File getFolderPath() {
        return folderPath;
    }

    @Override
    @NonNull
    public Map<String, String> getPerLanguageSettings() {
        return perLanguageSettings;
    }
}
