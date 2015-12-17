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
import com.android.annotations.Nullable;
import com.android.tools.perflib.analyzer.AnalysisReport;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.analyzer.Analyzer;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.android.tools.perflib.analyzer.Capture;
import com.android.tools.perflib.analyzer.CaptureGroup;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class MemoryAnalyzer extends Analyzer {

    private Set<MemoryAnalyzerTask> mTasks = new HashSet<MemoryAnalyzerTask>();

    private AnalysisReport mOutstandingReport;

    private ListenableFuture<List<List<AnalysisResultEntry>>> mRunningAnalyzers;

    private volatile boolean mCancelAnalysis = false;

    private boolean mAnalysisComplete = false;

    private static boolean accept(@NonNull Capture capture) {
        return Snapshot.TYPE_NAME.equals(capture.getTypeName());
    }

    @Override
    public boolean accept(@NonNull CaptureGroup captureGroup) {
        for (Capture capture : captureGroup.getCaptures()) {
            if (accept(capture)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Analyze the given {@code captureGroup}. It is highly recommended to call this method on the
     * same thread as that of the {@code synchronizingExecutor} to avoid race conditions.
     *
     * @param captureGroup          captures to analyze
     * @param synchronizingExecutor executor to synchronize the results aggregation
     * @param taskExecutor          executor service to run the analyzer tasks on
     * @return an AnalysisReport in which the caller can listen to
     */
    @NonNull
    @Override
    public AnalysisReport analyze(@NonNull CaptureGroup captureGroup,
            @NonNull Set<AnalysisReport.Listener> listeners,
            @NonNull Set<? extends AnalyzerTask> tasks,
            @NonNull final Executor synchronizingExecutor,
            @NonNull ExecutorService taskExecutor) {
        // TODO move this to Analyzer once Configuration is implemented
        if (mOutstandingReport != null) {
            return mOutstandingReport;
        }

        for (AnalyzerTask task : tasks) {
            if (task instanceof MemoryAnalyzerTask) {
                mTasks.add((MemoryAnalyzerTask) task);
            }
        }

        mOutstandingReport = new AnalysisReport();
        mOutstandingReport.addResultListeners(listeners);

        List<ListenableFutureTask<List<AnalysisResultEntry>>> futuresList
                = new ArrayList<ListenableFutureTask<List<AnalysisResultEntry>>>();

        for (final Capture capture : captureGroup.getCaptures()) {
            if (accept(capture)) {
                final Snapshot snapshot = capture.getRepresentation(Snapshot.class);
                if (snapshot == null) {
                    continue;
                }

                List<Heap> heapsToUse = new ArrayList<Heap>(snapshot.getHeaps().size());
                for (Heap heap : snapshot.getHeaps()) {
                    if ("app".equals(heap.getName())) {
                        heapsToUse.add(heap);
                        break;
                    }
                }
                final MemoryAnalyzerTask.Configuration configuration
                        = new MemoryAnalyzerTask.Configuration(heapsToUse);

                for (final MemoryAnalyzerTask task : mTasks) {
                    final ListenableFutureTask<List<AnalysisResultEntry>> futureTask =
                            ListenableFutureTask.create(new Callable<List<AnalysisResultEntry>>() {
                                @Override
                                public List<AnalysisResultEntry> call() throws Exception {
                                    if (mCancelAnalysis) {
                                        return null;
                                    }

                                    return task.analyze(configuration, snapshot);
                                }
                            });
                    Futures.addCallback(futureTask,
                            new FutureCallback<List<AnalysisResultEntry>>() {
                                @Override
                                public void onSuccess(List<AnalysisResultEntry> result) {
                                    if (mCancelAnalysis) {
                                        return;
                                    }

                                    mOutstandingReport.addAnalysisResultEntries(result);
                                }

                                @Override
                                public void onFailure(@Nullable Throwable t) {

                                }
                            }, synchronizingExecutor);
                    taskExecutor.submit(futureTask);
                    futuresList.add(futureTask);
                }
            }
        }

        mRunningAnalyzers = Futures.allAsList(futuresList);
        Futures.addCallback(mRunningAnalyzers,
                new FutureCallback<List<List<AnalysisResultEntry>>>() {
                    @Override
                    public void onSuccess(@Nullable List<List<AnalysisResultEntry>> result) {
                        mAnalysisComplete = true;
                        mOutstandingReport.setCompleted();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        mAnalysisComplete = true;
                        mOutstandingReport.setCancelled();
                    }
                }, synchronizingExecutor);
        return mOutstandingReport;
    }

    @Override
    public void cancel() {
        if (mOutstandingReport == null || mAnalysisComplete) {
            return;
        }

        mCancelAnalysis = true;
        mRunningAnalyzers.cancel(true);
        mOutstandingReport.setCancelled();
    }

    public boolean isRunning() {
        return !mRunningAnalyzers.isDone();
    }
}
