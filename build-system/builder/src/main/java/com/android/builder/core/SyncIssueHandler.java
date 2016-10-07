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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;

/**
 * Handler for issues during evaluation / sync.
 */
public interface SyncIssueHandler {

    /**
     * Reports an issue.
     *
     * @param data a data representing the source of the issue. This goes hand in hand with the
     *             <var>type</var>, and is not meant to be readable. Instead a (possibly translated)
     *             message is created from this data and type.
     * @param type the type of the issue.
     * @param severity the severity of the issue
     * @param msg a human readable issue (for command line output, or if an older IDE doesn't know
     *            this particular issue type.)
     * @return a SyncIssue if the issue.
     *
     * @see SyncIssue
     */
    @NonNull
    SyncIssue handleIssue(@Nullable String data, int type, int severity, @NonNull String msg);
}
