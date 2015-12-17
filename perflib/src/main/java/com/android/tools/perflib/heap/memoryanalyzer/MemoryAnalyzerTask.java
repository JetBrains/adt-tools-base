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
package com.android.tools.perflib.heap.memoryanalyzer;

import com.android.annotations.NonNull;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;

import java.util.Collection;
import java.util.List;

public abstract class MemoryAnalyzerTask implements AnalyzerTask {

    public static class Configuration {

        public Collection<Heap> mHeaps;

        public Configuration(@NonNull Collection<Heap> heaps) {
            mHeaps = heaps;
        }
    }

    abstract List<AnalysisResultEntry> analyze(@NonNull Configuration configuration,
            @NonNull Snapshot snapshot);
}
