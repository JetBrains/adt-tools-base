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
package com.android.sdklib.repository.legacy;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.UserCredentials;
import com.android.repository.api.ProgressIndicator;

/**
 * Implementation of {@link ITaskMonitor} that wraps a {@link ProgressIndicator}, for interaction
 * with the legacy SDK framework.
 */
class LegacyTaskMonitor implements ITaskMonitor {

    private final ProgressIndicator mWrapped;

    private int mProgressMax;

    LegacyTaskMonitor(ProgressIndicator toWrap) {
        mWrapped = toWrap;
        mProgressMax = 100;
    }

    @Override
    public void setDescription(String format, Object... args) {
        mWrapped.setText(String.format(format, args));
    }

    @Override
    public void log(String format, Object... args) {
        mWrapped.logInfo(String.format(format, args));
    }

    @Override
    public void logError(String format, Object... args) {
        mWrapped.logError(String.format(format, args));
    }

    @Override
    public void logVerbose(String format, Object... args) {
        // TODO
    }

    @Override
    public void setProgressMax(int max) {
        mProgressMax = max;
    }

    @Override
    public int getProgressMax() {
        return mProgressMax;
    }

    @Override
    public void incProgress(int delta) {
        mWrapped.setFraction(mWrapped.getFraction() + (double) delta / (double) mProgressMax);
    }

    @Override
    public int getProgress() {
        return (int)(mWrapped.getFraction() * mProgressMax);
    }

    @Override
    public boolean isCancelRequested() {
        return mWrapped.isCanceled();
    }

    @Override
    public ITaskMonitor createSubMonitor(int tickCount) {
        // TODO: implement if necessary
        return null;
    }

    @Override
    public boolean displayPrompt(String title, String message) {
        // TODO: implement if necessary
        return false;
    }

    @Override
    public UserCredentials displayLoginCredentialsPrompt(String title, String message) {
        // TODO: implement if necessary
        return null;
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        mWrapped.logError(String.format(msgFormat, args), t);
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        mWrapped.logWarning(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        mWrapped.logInfo(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        // TODO
    }
}
