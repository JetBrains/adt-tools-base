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
 * {@link ProgressIndicator} that just delegates all its functionality to another.
 */
public class DelegatingProgressIndicator implements ProgressIndicator {

    protected ProgressIndicator mWrapped;

    protected DelegatingProgressIndicator(@NonNull ProgressIndicator wrapped) {
        mWrapped = wrapped;
    }

    @Override
    public void setText(@Nullable String s) {
        mWrapped.setText(s);
    }

    @Override
    public boolean isCanceled() {
        return mWrapped.isCanceled();
    }

    @Override
    public void cancel() {
        mWrapped.cancel();
    }

    @Override
    public void setCancellable(boolean cancellable) {
        mWrapped.setCancellable(cancellable);
    }

    @Override
    public boolean isCancellable() {
        return mWrapped.isCancellable();
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        mWrapped.setIndeterminate(indeterminate);
    }

    @Override
    public boolean isIndeterminate() {
        return mWrapped.isIndeterminate();
    }

    @Override
    public void setFraction(double v) {
        mWrapped.setFraction(v);
    }

    @Override
    public double getFraction() {
        return mWrapped.getFraction();
    }

    @Override
    public void setSecondaryText(@Nullable String s) {
        mWrapped.setSecondaryText(s);
    }

    @Override
    public void logWarning(@NonNull String s) {
        mWrapped.logWarning(s);
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        mWrapped.logWarning(s, e);
    }

    @Override
    public void logError(@NonNull String s) {
        mWrapped.logError(s);
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        mWrapped.logError(s, e);
    }

    @Override
    public void logInfo(@NonNull String s) {
        mWrapped.logInfo(s);
    }
}
