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

    private boolean mCompleted = false;

    private boolean mCancelled = false;

    /**
     * Add the {@code entry} to the report. Since this is effectively a "reduce" call, the user will
     * most likely want to call this on the same thread as {@link #addResultListener(Listener)} and
     * {@link #setCompleted()}.
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
     * Add {@code listener} to the list of listeners listening for results or report completion. The
     * user will most likely want to run this on the same thread as {@link
     * #addAnalysisResultEntries(List)} and {@link #setCompleted()}.
     */
    public void addResultListener(@NonNull Listener listener) {
        if (mListeners.contains(listener)) {
            return;
        }

        mListeners.add(listener);
        listener.onResultsAdded(mAnalysisResults);
        if (mCompleted) {
            listener.onAnalysisComplete();
        } else if (mCancelled) {
            listener.onAnalysisCancelled();
        }
    }

    public void removeResultListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    public interface Listener {

        void onResultsAdded(@NonNull List<AnalysisResultEntry> entries);

        void onAnalysisComplete();

        void onAnalysisCancelled();
    }
}
