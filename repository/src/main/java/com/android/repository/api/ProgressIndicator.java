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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * A progress indicator and logger. Progress is from 0-1, or indeterminate.
 */
public interface ProgressIndicator {

    /**
     * Sets the main text shown in the progress indicator.
     */
    void setText(@Nullable String s);

    /**
     * @return True if the user has canceled this operation.
     */
    boolean isCanceled();

    /**
     * Try to cancel this operation.
     */
    void cancel();

    /**
     * Sets whether the user should be able to cancel this operation.
     */
    void setCancellable(boolean cancellable);

    /**
     * @return true if the user should be able to cancel this operation.
     */
    boolean isCancellable();

    /**
     * Sets whether this progress indicator should show indeterminate progress.
     */
    void setIndeterminate(boolean indeterminate);

    /**
     * @return true if this progress indicator is set to show indeterminate progress.
     */
    boolean isIndeterminate();

    /**
     * Sets how much progress should be shown on the progress bar, between 0 and 1.
     */
    void setFraction(double v);

    /**
     * @return The current amount of progress shown on the progress bar, between 0 and 1.
     */
    double getFraction();

    /**
     * Sets the secondary text on the progress indicator.
     */
    void setSecondaryText(@Nullable String s);

    /**
     * Logs a warning.
     */
    void logWarning(@NonNull String s);
    /**
     * Logs a warning, including a stacktrace.
     */
    void logWarning(@NonNull String s, @Nullable Throwable e);

    /**
     * Logs an error.
     */
    void logError(@NonNull String s);

    /**
     * Logs an error, including a stacktrace.
     */
    void logError(@NonNull String s, @Nullable Throwable e);

    /**
     * Logs an info message.
     */
    void logInfo(@NonNull String s);

}
