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

package com.android.tools.pixelprobe;

/**
 * A guide is a horizontal or vertical line set at a specific location
 * in an image. Guides can be used to align layers and other graphics
 * elements during the creation process. Guides might be extracted by
 * the image decoding process.
 */
public final class Guide {
    private final Orientation mOrientation;
    private final float mPosition;

    /**
     * Defines a guide's orientation.
     */
    public enum Orientation {
        /**
         * A vertical guide's position must be treated as an X coordinate.
         */
        VERTICAL,
        /**
         * A horizontal guide's position must be treated as a Y coordinate.
         */
        HORIZONTAL
    }

    /**
     * Creates a new guide with the specified position and orientation.
     *
     * @param orientation The guide's orientation
     * @param position The guide's position in pixels
     */
    Guide(Orientation orientation, float position) {
        mOrientation = orientation;
        mPosition = position;
    }

    /**
     * Returns this guide's orientation.
     */
    public Orientation getOrientation() {
        return mOrientation;
    }

    /**
     * Returns this guide's position in pixels.
     * If the guide is horizontal, the returned value is a Y coordinate.
     * If the guide is vertical, the returned value is an X coordinate.
     */
    public float getPosition() {
        return mPosition;
    }

    @Override
    public String toString() {
        return "Guide{" +
                "orientation=" + mOrientation +
                ", position=" + String.format("%.2f", mPosition) +
                '}';
    }
}
