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

package com.android.builder.model;

import java.io.File;
import java.util.Map;

/**
 * A native source folder with compile settings for each file type.
 */
public interface NativeFolder {

    /**
     * Folder containing the source files.
     */
    File getFolderPath();

    /**
     * The compile settings for each file type.
     *
     * The key is the file type, which can be "c" or "c++".  The value is the name of a
     * {@link NativeSettings}.
     */
    Map<String, String> getPerLanguageSettings();
}
