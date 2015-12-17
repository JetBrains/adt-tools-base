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
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * When activities are destroyed, there should be no external references to them. Since activities
 * are normally GC root objects, this implies no reference should be pointing to these activities if
 * they are to be GC'ed. This analyzer detects activities that have been destroyed but still have
 * some non-system reference(s) pointing to them, indicating a memory leak.
 */
public class LeakedActivityAnalyzerTask extends MemoryAnalyzerTask {

    @Override
    List<AnalysisResultEntry> analyze(@NonNull Configuration configuration,
            @NonNull Snapshot snapshot) {
        List<Instance> leakingInstances = new ArrayList<Instance>();

        List<ClassObj> activityClasses = snapshot.findAllDescendantClasses("android.app.Activity");
        for (ClassObj activityClass : activityClasses) {
            List<Instance> instances = new ArrayList<Instance>();
            for (Heap heap : configuration.mHeaps) {
                instances.addAll(activityClass.getHeapInstances(heap.getId()));
            }

            for (Instance instance : instances) {
                Instance immediateDominator = instance.getImmediateDominator();
                if (!(instance instanceof ClassInstance) || immediateDominator == null) {
                    continue;
                }

                for (ClassInstance.FieldValue value : ((ClassInstance) instance).getValues()) {
                    if ("mFinished".equals(value.getField().getName()) || "mDestroyed"
                            .equals(value.getField().getName())) {
                        if (instance.getDistanceToGcRoot() != Integer.MAX_VALUE && value
                                .getValue() instanceof Boolean &&
                                (Boolean) value.getValue()) {
                            leakingInstances.add(instance);
                            break;
                        }
                    }
                }
            }
        }

        List<AnalysisResultEntry> results = new ArrayList<AnalysisResultEntry>(
                leakingInstances.size());
        for (Instance instance : leakingInstances) {
            results.add(new LeakedActivityEntry(instance.getClassObj().getClassName(),
                    instance));
        }
        return results;
    }

    @NonNull
    @Override
    public String getTaskName() {
        return "Detect Leaked Activities";
    }

    @NonNull
    @Override
    public String getTaskDescription() {
        return "Detects leaked activities in Android applications.";
    }

    public static class LeakedActivityEntry extends MemoryAnalysisResultEntry {

        private LeakedActivityEntry(@NonNull String offenseDescription,
                @NonNull Instance offendingInstance) {
            super(offenseDescription, Collections.singletonList(offendingInstance));
        }

        @NonNull
        @Override
        public String getWarningMessage() {
            return mOffender.getOffendingDescription();
        }

        @NonNull
        @Override
        public String getCategory() {
            return "Leaked Activities";
        }
    }
}
