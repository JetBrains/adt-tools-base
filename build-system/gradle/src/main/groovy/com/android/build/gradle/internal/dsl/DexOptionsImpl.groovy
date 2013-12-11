/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.builder.DexOptions
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

public class DexOptionsImpl implements DexOptions {

    @Input
    private boolean coreLibraryFlag

    @Input
    private boolean isIncrementalFlag = false

    @Input
    private boolean isPreDexLibrariesFlag = true

    @Input
    private boolean isJumboModeFlag = false

    @Input
    @Optional
    private String javaMaxHeapSize

    public void setCoreLibrary(boolean coreLibrary) {
        coreLibraryFlag = coreLibrary
    }

    @Override
    boolean isCoreLibrary() {
        return coreLibraryFlag
    }

    public void setIncremental(boolean isIncremental) {
        isIncrementalFlag = isIncremental
    }

    @Override
    boolean getIncremental() {
        return false; // incremental support is broken.
        //return isIncrementalFlag
    }

    @Override
    boolean getPreDexLibraries() {
        return isPreDexLibrariesFlag
    }

    void setPreDexLibraries(boolean flag) {
        isPreDexLibrariesFlag = flag
    }

    public void setJumboMode(boolean flag) {
        isJumboModeFlag = flag
    }

    @Override
    boolean getJumboMode() {
        return isJumboModeFlag
    }

    public void setJavaMaxHeapSize(String theJavaMaxHeapSize) {
        if (theJavaMaxHeapSize.matches("\\d+[kKmMgGtT]?")) {
            javaMaxHeapSize = theJavaMaxHeapSize
        } else {
            throw new IllegalArgumentException(
                    "Invalid max heap size DexOption. See `man java` for valid -Xmx arguments.")
        }
    }

    @Override
    public String getJavaMaxHeapSize() {
        return javaMaxHeapSize
    }
}
