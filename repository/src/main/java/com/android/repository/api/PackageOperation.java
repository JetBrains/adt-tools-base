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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * An install or uninstall operation that affects the current SDK state.
 */
public interface PackageOperation {

    /**
     * Statuses of in-progress operations.
     */
    enum InstallStatus {
        /**
         * This installer hasn't started yet
         */
        NOT_STARTED,
        /**
         * This installer is in the process of preparing the component for install. No changes are
         * made to the SDK during this phase.
         */
        PREPARING,
        /**
         * The steps that can be taken without affecting the installed SDK have completed.
         */
        PREPARED,
        /**
         * The SDK is being modified.
         */
        INSTALLING,
        /**
         * The installation has completed successfully.
         */
        FAILED,
        /**
         * The installation has ended unsuccessfully.
         */
        COMPLETE,
        /**
         * Uninstall is starting.
         */
        UNINSTALL_STARTING,
        /**
         * Uninstall has completed.
         */
        UNINSTALL_COMPLETE
    }

    /**
     * The package (local or remote) that's being affected.
     */
    @NonNull
    RepoPackage getPackage();

    /**
     * Registers a listener that will be called when the {@link InstallStatus} of this installer
     * changes.
     */
    void registerStateChangeListener(@NonNull StatusChangeListener listener);

    /**
     * Gets the current {@link InstallStatus} of this installer.
     */
    @NonNull
    InstallStatus getInstallStatus();

    /**
     * A listener that will be called when the {@link #getInstallStatus() status} of this installer
     * changes.
     */
    interface StatusChangeListener {

        void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress)
                throws PackageOperation.StatusChangeListenerException;
    }

    /**
     * Exception thrown by a {@link StatusChangeListener}.
     */
    class StatusChangeListenerException extends Exception {

        public StatusChangeListenerException(@Nullable Exception e) {
            super(e);
        }

        public StatusChangeListenerException(@Nullable String reason) {
            super(reason);
        }
    }
}
