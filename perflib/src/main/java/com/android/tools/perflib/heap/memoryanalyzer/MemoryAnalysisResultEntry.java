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
import com.android.tools.perflib.analyzer.Offender;
import com.android.tools.perflib.heap.Instance;

import java.util.List;

public abstract class MemoryAnalysisResultEntry implements AnalysisResultEntry<Instance> {

    @NonNull
    protected Offender<Instance> mOffender;

    protected MemoryAnalysisResultEntry(@NonNull String offenseDescription,
            @NonNull List<Instance> offendingInstance) {
        mOffender = new Offender<Instance>(offenseDescription, offendingInstance);
    }

    @NonNull
    @Override
    public Offender<Instance> getOffender() {
        return mOffender;
    }
}
