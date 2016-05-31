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

package com.android.tools.pixelprobe.effect;

import com.android.tools.pixelprobe.BlendMode;

import java.awt.*;

/**
 * Represents either an inner or an outer ("drop") shadow.
 */
public final class Shadow {
    private final Type type;
    private final float blur;
    private final float angle;
    private final float distance;
    private final float opacity;
    private final Color color;
    private final BlendMode blendMode;

    /**
     * Type of shadow.
     */
    public enum Type {
        INNER,
        OUTER
    }

    Shadow(Builder builder) {
        type = builder.type;
        blur = builder.blur;
        angle = builder.angle;
        distance = builder.distance;
        opacity = builder.opacity;
        color = builder.color;
        blendMode = builder.blendMode;
    }

    /**
     * Returns this shadow's blur amount in pixels.
     */
    public float getBlur() {
        return blur;
    }

    /**
     * Returns this shadow's casting angle in degrees.
     */
    public float getAngle() {
      return angle;
    }

    /**
     * Returns this shadow's distance to the source in pixels.
     */
    public float getDistance() {
        return distance;
    }

    /**
     * Returns this shadow's opacity between 0 and 1.
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * Returns this shadow's color.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Returns this shadow's blending mode.
     */
    public BlendMode getBlendMode() {
        return blendMode;
    }

    /**
     * Returns this shadow's type.
     */
    public Type getType() {
        return type;
    }

    @SuppressWarnings("UseJBColor")
    public static final class Builder {
        private float blur;
        private float angle;
        private float distance;
        private float opacity;
        private Color color = Color.BLACK;
        private BlendMode blendMode = BlendMode.NORMAL;

        private final Type type;

        public Builder(Type type) {
            this.type = type;
        }

        public Builder blur(float blur) {
            this.blur = blur;
            return this;
        }

        public Builder angle(float angle) {
            this.angle = angle;
            return this;
        }

        public Builder distance(float distance) {
            this.distance = distance;
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public Builder color(Color color) {
            this.color = color;
            return this;
        }

        public Builder blendMode(BlendMode blendMode) {
            this.blendMode = blendMode;
            return this;
        }

        public Shadow build() {
            return new Shadow(this);
        }
    }
}
