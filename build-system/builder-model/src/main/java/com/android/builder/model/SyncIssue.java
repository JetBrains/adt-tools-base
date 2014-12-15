/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;

/**
 * Class representing a sync issue.
 * The goal is to make these issues not fail the sync but instead report them at the end
 * of a successful sync.
 */
public interface SyncIssue {
    public static final int SEVERITY_WARNING = 1;
    public static final int SEVERITY_ERROR = 2;

    public static final int TYPE_NONE                     = 0;
    public static final int TYPE_PLUGIN_OBSOLETE          = 1;
    public static final int TYPE_UNRESOLVED_DEPENDENCY    = 2;
    public static final int TYPE_DEPENDENCY_IS_APK        = 3;
    public static final int TYPE_DEPENDENCY_IS_APKLIB     = 4;
    public static final int TYPE_MAX                      = 5; // increment when adding new types.

    /**
     * Returns the severity of the issue.
     */
    int getSeverity();

    /**
     * Returns the type of the issue.
     */
    int getType();

    /**
     * Returns the data of the issue.
     *
     * This is a machine-readable string used by the IDE for known issue types.
     */
    @NonNull
    String getData();

    /**
     * Returns the a user-readable message for the issue.
     *
     * This is used by IDEs that do not recognize the issue type (ie older IDE released before
     * the type was added to the plugin).
     */
    @NonNull
    String getMessage();
}
