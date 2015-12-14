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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.MessageReceiver;

/**
 * An error reporter for project evaluation and execution.
 *
 * The behavior of the reporter must vary depending on the evaluation mode
 * ({@link ErrorReporter.EvaluationMode}), indicating whether
 * the IDE is querying the project or not.
 */
public abstract class ErrorReporter implements MessageReceiver {

    public enum EvaluationMode {
        /** Standard mode, errors should be breaking */
        STANDARD,
        /**
         * IDE mode. Errors should not be breaking and should generate a SyncIssue instead.
         */
        IDE,
        /** Legacy IDE mode (Studio 1.0), where SyncIssue are not understood by the IDE. */
        IDE_LEGACY
    }

    @NonNull
    private final EvaluationMode mMode;

    protected ErrorReporter(@NonNull EvaluationMode mode) {
        mMode = mode;
    }

    @NonNull
    public EvaluationMode getMode() {
        return mMode;
    }

    /**
     * Reports an error.
     *
     * <p>The behavior of this method depends on whether the project is being evaluated by
     * an IDE or from the command line. If it's the former, the error will simply be recorder
     * and displayed after the sync properly finishes. If it's the latter, then the evaluation
     * is aborted right away.
     *
     * @param data a data representing the source of the error. This goes hand in hand with the
     *             <var>type</var>, and is not meant to be readable. Instead a (possibly translated)
     *             message is created from this data and type.
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     *            this particular issue type.)
     * @return a SyncIssue if the error is only recorded.
     *
     * @see SyncIssue
     */
    @NonNull
    public final SyncIssue handleSyncError(@Nullable String data, int type, @NonNull String msg) {
        return handleSyncIssue(data, type, SyncIssue.SEVERITY_ERROR, msg);
    }

    /**
     * Reports a warning.
     *
     * <p>Behaves similar to {@link #handleSyncError(String, int, String)} but does not abort the
     * build.
     *
     * @see #handleSyncError(String, int, String)
     */
    @NonNull
    public final SyncIssue handleSyncWarning(@Nullable String data, int type, @NonNull String msg) {
        return handleSyncIssue(data, type, SyncIssue.SEVERITY_WARNING, msg);
    }

    @NonNull
    protected abstract SyncIssue handleSyncIssue(
            @Nullable String data,
            int type,
            int severity,
            @NonNull String msg);
}
