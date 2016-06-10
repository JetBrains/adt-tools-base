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

package com.android.builder.profile;

import com.google.wireless.android.sdk.stats.AndroidStudioStats;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Used to get the memory statistics about the currently running JVM, such as GC time.
 */
public class MemoryStats {

    /** Gets the current memory properties */
    static AndroidStudioStats.GradleBuildMemorySample getCurrentProperties() {
        long gcTime = 0;
        long gcCount = 0;
        for(GarbageCollectorMXBean g: ManagementFactory.getGarbageCollectorMXBeans()) {
            if (g.getCollectionTime() != -1) {
                gcTime += g.getCollectionTime();
            }
            if (g.getCollectionCount() != -1) {
                gcCount += g.getCollectionCount();
            }
        }

        return AndroidStudioStats.GradleBuildMemorySample.newBuilder()
                .setGcCount(gcCount)
                .setGcTimeMs(gcTime)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }
}
