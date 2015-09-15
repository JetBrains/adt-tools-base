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
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.collect.HashMultimap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DuplicateStringsAnalyzerTask extends MemoryAnalyzerTask {

    @Override
    List<AnalysisResultEntry> analyze(@NonNull Configuration configuration,
            @NonNull Snapshot snapshot) {
        List<AnalysisResultEntry> results = new ArrayList<AnalysisResultEntry>();

        HashMultimap<String, ClassInstance> stringIndex = HashMultimap.create();
        ClassObj stringClass = snapshot.findClass("java.lang.String");
        if (stringClass == null) {
            return Collections.emptyList();
        }

        for (Heap heap : configuration.mHeaps) {
            List<Instance> instances = stringClass.getHeapInstances(heap.getId());

            for (Instance instance : instances) {
                assert instance instanceof ClassInstance;
                ClassInstance stringInstance = (ClassInstance) instance;
                char[] characters = stringInstance.getStringChars();
                if (characters != null) {
                    String string = new String(characters);
                    stringIndex.put(string, stringInstance);
                }
            }
        }

        for (String key : stringIndex.keySet()) {
            Set<ClassInstance> classInstanceSet = stringIndex.get(key);
            if (classInstanceSet.size() > 1) {
                results.add(
                        new DuplicateStringsEntry(key, new ArrayList<Instance>(classInstanceSet)));
            }
        }

        return results;
    }

    @NonNull
    @Override
    public String getTaskName() {
        return "Duplicate Strings Analyzer";
    }

    @NonNull
    @Override
    public String getTaskDescription() {
        return "Detects duplicate strings in the application.";
    }

    private static class DuplicateStringsEntry implements AnalysisResultEntry {

        @NonNull
        private Offender mOffender;

        private DuplicateStringsEntry(@NonNull String offendingString,
                @NonNull List<Instance> duplicates) {
            mOffender = new Offender(offendingString, duplicates);
        }

        @NonNull
        @Override
        public String getWarningMessage() {
            // Ironic calling this multiple times will result in duplicate strings as well.
            return "Duplicate String: \"" + mOffender.getOffendingDescription() + "\"";
        }

        @NonNull
        @Override
        public String getCategory() {
            return "DuplicateStrings";
        }

        @NonNull
        @Override
        public Offender getOffender() {
            return mOffender;
        }
    }
}
