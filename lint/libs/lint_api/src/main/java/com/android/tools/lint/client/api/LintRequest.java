/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.annotations.Beta;

import java.io.File;
import java.util.EnumSet;
import java.util.List;

/**
 * Information about a request to run lint
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class LintRequest {
    @NonNull
    private final LintClient mClient;

    @NonNull
    private final List<File> mFiles;

    @Nullable
    private EnumSet<Scope> mScope;

    @Nullable
    private Boolean mReleaseMode;

    /**
     * Creates a new {@linkplain LintRequest}, to be passed to a {@link LintDriver}
     *
     * @param client the tool wrapping the analyzer, such as an IDE or a CLI
     * @param files the set of files to check with lint. This can reference Android projects,
     *          or directories containing Android projects, or individual XML or Java files
     *          (typically for incremental IDE analysis).
     *
     * @return the set of files to check, should not be empty
     *
     */
    public LintRequest(@NonNull LintClient client, @NonNull List<File> files) {
        mClient = client;
        mFiles = files;
    }

    /**
     * Returns the lint client requesting the lint check
     *
     * @return the client, never null
     */
    @NonNull
    public LintClient getClient() {
        return mClient;
    }

    /**
     * Returns the set of files to check with lint. This can reference Android projects,
     * or directories containing Android projects, or individual XML or Java files
     * (typically for incremental IDE analysis).
     *
     * @return the set of files to check, should not be empty
     */
    @NonNull
    public List<File> getFiles() {
        return mFiles;
    }

    /**
     * Sets the scope to use; lint checks which require a wider scope set
     * will be ignored
     *
     * @return the scope to use, or null to use the default
     */
    @Nullable
    public EnumSet<Scope> getScope() {
        return mScope;
    }

    /**
     * Sets the scope to use; lint checks which require a wider scope set
     * will be ignored
     *
     * @param scope the scope
     * @return this, for constructor chaining
     */
    @NonNull
    public LintRequest setScope(@Nullable EnumSet<Scope> scope) {
        mScope = scope;
        return this;
    }

    /**
     * Returns {@code true} if lint is invoked as part of a release mode build,
     * {@code false}  if it is part of a debug mode build, and {@code null} if
     * the release mode is not known
     *
     * @return true if this lint is running in release mode, null if not known
     */
    @Nullable
    public Boolean isReleaseMode() {
        return mReleaseMode;
    }

    /**
     * Sets the release mode. Use {@code true} if lint is invoked as part of a
     * release mode build, {@code false} if it is part of a debug mode build,
     * and {@code null} if the release mode is not known
     *
     * @param releaseMode true if this lint is running in release mode, null if not known
     * @return this, for constructor chaining
     */
    @NonNull
    public LintRequest setReleaseMode(@Nullable Boolean releaseMode) {
        mReleaseMode = releaseMode;
        return this;
    }
}
