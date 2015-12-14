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
package com.android.tools.perflib.heap.analysis;

import com.android.annotations.NonNull;

public class ComputationProgress {
    @NonNull
    private String mMessage;

    // Progress is a number in [0,1], where 0 represents just started, and 1 is done.
    private double mProgress;

    public ComputationProgress(@NonNull String message, double progress) {
        mMessage = message;
        mProgress = progress;
    }

    @NonNull
    public String getMessage() {
        return mMessage;
    }

    public void setMessage(@NonNull String message) {
        mMessage = message;
    }

    public double getProgress() {
        return mProgress;
    }

    public void setProgress(double progress) {
        mProgress = progress;
    }
}
