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

package com.android.build.api.transform;

import java.io.File;
import java.util.Collection;

/**
 * A secondary input file for a {@link Transform}.
 *
 * A secondary input is part of the transform inputs and can be decorated to indicate if a change
 * to the input would trigger a non incremental
 * {@link Transform#transform(Context, Collection, Collection, Collection, TransformOutputProvider, boolean)}
 * call
 */
public class SecondaryFile {

    private final boolean supportsIncrementalBuild;
    private final File secondaryInputFile;

    public SecondaryFile(File secondaryInputFile, boolean supportsIncrementalBuild) {
        this.supportsIncrementalBuild = supportsIncrementalBuild;
        this.secondaryInputFile = secondaryInputFile;
    }

    /**
     * Returns true if this secondary input changes can be handled by the receiving {@link Transform}
     * incrementally. If false, a change to the file returned by {@link #getFile()} will trigger
     * a non incremental build.
     * @return true when the input file changes can be handled incrementally, false otherwise.
     */
    public boolean supportsIncrementalBuild() {
        return supportsIncrementalBuild;
    }

    /**
     * Returns the file handle for this secondary input to a Transform.
     * @return a file handle.
     */
    public File getFile() {
        return secondaryInputFile;
    }
}
