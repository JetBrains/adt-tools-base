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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Used to get the memory statistics about the currently running JVM, such as GC time.
 */
public class MemoryStats {

    /** Gets the current memory properties */
    public static Properties getCurrentProperties() {
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

        return new Properties(gcTime, gcCount, System.currentTimeMillis());
    }

    /**
     * Memory properties.
     */
    public static class Properties {
        private final long mGcTime;
        private final long mGcCount;
        private final long mTimestamp;

        public Properties(long gcTime, long gcCount, long timestamp) {
            mGcTime = gcTime;
            mGcCount = gcCount;
            mTimestamp = timestamp;
        }

        public long getGcTime() {
            return mGcTime;
        }

        public long getGcCount() {
            return mGcCount;
        }

        public long getTimestamp() {
            return mTimestamp;
        }
    }
}
