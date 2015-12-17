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
package com.android.tools.perflib.analyzer;

import com.android.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnalysisReport {

    @NonNull
    private Set<Listener> mListeners = new HashSet<Listener>();

    @NonNull
    private List<AnalysisResultEntry> mAnalysisResults = new ArrayList<AnalysisResultEntry>();

    // volatile so other threads can see the updated value, but this is not intrinsically
    // thread-safe. This is mainly useful for the UI to know that the analysis is complete and
    // reflect this fact in the UI.
    private volatile boolean mCompleted = false;

    // volatile for similar reasons as mCompleted.
    private volatile boolean mCancelled = false;

    /**
     * Add the {@code entry} to the report. Since this is effectively a "reduce" call, the listener
     * will want to get called on the same thread as {@link #addResultListener(Listener)} and {@link
     * #setCompleted()}.
     */
    public void addAnalysisResultEntries(@NonNull List<AnalysisResultEntry> entries) {
        mAnalysisResults.addAll(entries);
        for (Listener listener : mListeners) {
            listener.onResultsAdded(entries);
        }
    }

    /**
     * Notifies all listeners that this analysis report has been completed. Mainly used for UI
     * feedback.
     */
    public void setCompleted() {
        if (mCompleted || mCancelled) {
            return;
        }

        mCompleted = true;
        for (Listener listener : mListeners) {
            listener.onAnalysisComplete();
        }
    }

    public void setCancelled() {
        if (mCompleted || mCancelled) {
            return;
        }

        mCancelled = true;
        for (Listener listener : mListeners) {
            listener.onAnalysisCancelled();
        }
    }

    /**
     * Adds all {@code listeners} to the set of listeners listening for results or report
     * completion. The caller will need to add them before analysis starts.
     */
    public void addResultListeners(@NonNull Set<Listener> listeners) {
        mListeners.addAll(listeners);
    }

    public void removeResultListener(@NonNull Set<Listener> listener) {
        mListeners.removeAll(listener);
    }

    public interface Listener {

        void onResultsAdded(@NonNull List<AnalysisResultEntry> entries);

        void onAnalysisComplete();

        void onAnalysisCancelled();
    }
}
