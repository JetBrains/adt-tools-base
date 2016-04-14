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
 * A simple {@link ProgressIndicator} that prints log messages to {@code stdout} and {@code stderr}.
 */
public class ConsoleProgressIndicator extends ProgressIndicatorAdapter {

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        System.err.println("Warning: " + s);
        if (e != null) {
            e.printStackTrace();
        }
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        System.err.println("Error: " + s);
        if (e != null) {
            e.printStackTrace();
        }

    }

    @Override
    public void logInfo(@NonNull String s) {
        System.out.println("Info: " + s);
    }
}
