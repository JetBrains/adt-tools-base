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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.repository.api.ProgressIndicator;
import com.google.common.collect.Lists;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Fake {@link ProgressIndicator} that keeps track of the messages that were logged to it, and
 * provides a convenient method for asserting that no errors or warnings occurred.
 */
public class FakeProgressIndicator implements ProgressIndicator {

    private List<String> mInfos = Lists.newArrayList();

    private List<String> mWarnings = Lists.newArrayList();

    private List<String> mErrors = Lists.newArrayList();

    private boolean mCancelled = false;

    private boolean mCancellable = true;

    @Override
    public void setText(String s) {

    }

    @Override
    public boolean isCanceled() {
        return mCancelled;
    }

    @Override
    public void cancel() {
        if (mCancellable) {
            mCancelled = true;
        }
    }

    @Override
    public void setCancellable(boolean cancellable) {
        mCancellable = cancellable;
    }

    @Override
    public boolean isCancellable() {
        return mCancellable;
    }

    @Override
    public void setFraction(double v) {

    }

    @Override
    public double getFraction() {
        return 0;
    }

    @Override
    public void setSecondaryText(String s) {

    }

    private static String getStackTrace() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        return sw.toString();
    }

    @Override
    public void logWarning(@NonNull String s) {
        mWarnings.add(s);
        mWarnings.add(getStackTrace());
    }

    @Override
    public void logWarning(@NonNull String s, Throwable e) {
        mWarnings.add(s + "\n" + e.toString());
        mWarnings.add(getStackTrace());
    }

    @Override
    public void logError(@NonNull String s) {
        mErrors.add(s);
        mErrors.add(getStackTrace());
    }

    @Override
    public void logError(@NonNull String s, Throwable e) {
        mErrors.add(s + "\n" + e.toString());
        mErrors.add(getStackTrace());
    }

    @Override
    public void logInfo(@NonNull String s) {
        mInfos.add(s);
    }

    @Override
    public boolean isIndeterminate() {
        return false;
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {

    }

    public List<String> getInfos() {
        return mInfos;
    }

    public List<String> getWarnings() {
        return mWarnings;
    }

    public List<String> getErrors() {
        return mErrors;
    }

    /**
     * {@code assert} that no errors or warnings have been logged.
     */
    public void assertNoErrorsOrWarnings(){
        if (!getErrors().isEmpty()) {
            throw new Error(getErrors().toString());
        }
        if (!getWarnings().isEmpty()) {
            throw new Error(getWarnings().toString());
        }

    }
}
