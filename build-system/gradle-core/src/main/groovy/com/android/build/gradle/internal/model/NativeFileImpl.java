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
import com.android.builder.model.NativeFile;

import java.io.File;
import java.io.Serializable;

/**
 * Implementation of {@link NativeFile}.
 */
public class NativeFileImpl implements NativeFile, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private File filePath;
    @NonNull
    private String settingsName;

    public NativeFileImpl(@NonNull File filePath, @NonNull String settingsName) {
        this.filePath = filePath;
        this.settingsName = settingsName;
    }

    @Override
    @NonNull
    public File getFilePath() {
        return filePath;
    }

    @Override
    @NonNull
    public String getSettingsName() {
        return settingsName;
    }
}
