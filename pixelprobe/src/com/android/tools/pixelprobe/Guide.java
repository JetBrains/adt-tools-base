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
    private final Orientation orientation;
    private final float position;

    Guide(Builder builder) {
        orientation = builder.orientation;
        position = builder.position;
    }

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
     * Returns this guide's orientation.
     */
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Returns this guide's position in pixels.
     * If the guide is horizontal, the returned value is a Y coordinate.
     * If the guide is vertical, the returned value is an X coordinate.
     */
    public float getPosition() {
        return position;
    }

    public static final class Builder {
        Orientation orientation;
        float position;

        public Builder orientation(Orientation orientation) {
            this.orientation = orientation;
            return this;
        }

        public Builder position(float position) {
            this.position = position;
            return this;
        }

        public Guide build() {
            return new Guide(this);
        }
    }

    @Override
    public String toString() {
        return "Guide{" +
               "orientation=" + orientation +
               ", position=" + String.format("%.2f", position) +
               '}';
    }
}
