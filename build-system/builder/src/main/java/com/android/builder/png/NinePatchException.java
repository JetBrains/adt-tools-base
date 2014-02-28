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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;

import java.io.File;

/**
 */
public class NinePatchException extends Exception {

    @NonNull
    private final File mFile;
    private final TickException mTickException;
    private final String mEdge;

    NinePatchException(
            @NonNull File file,
            @NonNull TickException tickException,
            @NonNull String edge) {

        mFile = file;
        mTickException = tickException;
        mEdge = edge;
    }

    NinePatchException(@NonNull File file, String message) {
        super(message);
        mFile = file;
        mTickException = null;
        mEdge = null;
    }

    @VisibleForTesting
    String getEdge() {
        return mEdge;
    }

    @VisibleForTesting
    TickException getTickException() {
        return mTickException;
    }

    @Override
    public String getMessage() {
        if (mTickException != null) {
            String info;
            if (mTickException.getPixelColor() != null && mTickException.getPixelLocation() >= 0) {
                info = String.format("Found at pixel #%d with color 0x%08X along %s edge",
                        mTickException.getPixelLocation(),
                        mTickException.getPixelColor(),
                        mEdge);
            } else {
                info = String.format("Found along %s edge", mEdge);
            }

            return String.format("%s: Error: 9-patch image malformed:\n       %s\n       %s",
                    mFile, mTickException.getMessage(), info);
        }

        return String.format("%s: Error: 9-patch image malformed: %s", mFile, getMessage());
    }
}
