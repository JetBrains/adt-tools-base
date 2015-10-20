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

package com.android.build.gradle.managed;

import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A source file for a native library.
 */
@Managed
public interface NativeSourceFolder {

    /**
     * The source folder.
     */
    File getSrc();
    void setSrc(File src);

    /**
     * List of compiler flags for all C files.
     */
    List<String> getCFlags();

    /**
     * List of compiler flags for all C++ files.
     */
    List<String> getCppFlags();
}
