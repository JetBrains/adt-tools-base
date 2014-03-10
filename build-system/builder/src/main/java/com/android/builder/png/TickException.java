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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;

/**
 */
class TickException extends Exception {

    /**
     * Bad pixel location. -1 if unknown or not relevant.
     */
    private final int mPixelLocation;

    /**
     * Bad pixel color. null if unknown or not relevant.
     */
    @Nullable
    private final Integer mPixelColor;

    static TickException createWithColor(@NonNull String message, int color) {
        return new TickException(message, -1,  color);
    }

    TickException(@NonNull String message, int pixelLocation, @Nullable Integer pixelColor) {
        super(message);
        mPixelLocation = pixelLocation;
        mPixelColor = pixelColor;
    }

    TickException(@NonNull String message) {
        this(message, -1, null);
    }

    TickException(@NonNull TickException tickException, int pixelLocation) {
        this(tickException.getMessage(), pixelLocation, tickException.getPixelColor());
    }

    @VisibleForTesting
    int getPixelLocation() {
        return mPixelLocation;
    }

    @VisibleForTesting
    @Nullable
    Integer getPixelColor() {
        return mPixelColor;
    }
}
