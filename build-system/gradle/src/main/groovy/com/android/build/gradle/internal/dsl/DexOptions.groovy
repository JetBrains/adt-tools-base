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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
/**
 * DSL object for configuring dx options.
 */
public class DexOptions implements com.android.builder.core.DexOptions {

    @Input
    private boolean isIncrementalFlag = false

    @Input
    private boolean isPreDexLibrariesFlag = true

    @Input
    private boolean isJumboModeFlag = false

    private int threadCount = 4

    @Input
    @Optional
    private String javaMaxHeapSize

    public void setIncremental(boolean isIncremental) {
        isIncrementalFlag = isIncremental
    }

    /**
     * Whether to enable the incremental mode for dx. This has many limitations and may not
     * work. Use carefully.
     */
    @Override
    boolean getIncremental() {
        return isIncrementalFlag
    }

    /**
     * Whether to pre-dex libraries. This can improve incremental builds, but clean builds may
     * be slower.
     */
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

    /**
     * Enable jumbo mode in dx (--force-jumbo).
     */
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

    /**
     * Sets the -JXmx* value when calling dx. Format should follow the 1024M pattern.
     */
    @Override
    public String getJavaMaxHeapSize() {
        return javaMaxHeapSize
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount
    }

    /**
     * TODO: Document.
     */
    @Override
    int getThreadCount() {
        return threadCount
    }

}
