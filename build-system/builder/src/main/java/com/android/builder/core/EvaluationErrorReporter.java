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
import com.android.builder.model.SyncIssue;

/**
 * An error reporter for project evaluation.
 *
 * The behavior of the reporter must vary depending on the evaluation mode
 * ({@link EvaluationErrorReporter.EvaluationMode}), indicating whether
 * the IDE is querying the project or not.
 */
public abstract class EvaluationErrorReporter {

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

    protected EvaluationErrorReporter(@NonNull EvaluationMode mode) {
        mMode = mode;
    }

    @NonNull
    public EvaluationMode getMode() {
        return mMode;
    }

    @NonNull
    public abstract SyncIssue handleSyncError(@NonNull String data, int type, @NonNull String msg);
}
