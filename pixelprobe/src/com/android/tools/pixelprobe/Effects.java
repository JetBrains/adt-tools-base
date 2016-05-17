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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the effects associated to an image's layer.
 */
public final class Effects {
    /**
     * Represents either an inner or an outer ("drop") shadow.
     */
    public static final class Shadow {
        private final float mBlur;
        private final float mAngle;
        private final float mDistance;
        private final float mOpacity;
        private final Color mColor;
        private final BlendMode mBlendMode;

        private Shadow(float blur, float angle, float distance, float opacity,
                Color color, BlendMode blendMode) {
            mBlur = blur;
            mAngle = angle;
            mDistance = distance;
            mOpacity = opacity;
            mColor = color;
            mBlendMode = blendMode;
        }

        /**
         * Returns this shadow's blur amount in pixels.
         */
        public float getBlur() {
            return mBlur;
        }

        /**
         * Returns this shadow's casting angle in degrees.
         */
        public float getAngle() {
            return mAngle;
        }

        /**
         * Returns this shadow's distance to the source in pixels.
         */
        public float getDistance() {
            return mDistance;
        }

        /**
         * Returns this shadow's opacity between 0 and 1.
         */
        public float getOpacity() {
            return mOpacity;
        }

        /**
         * Returns this shadow's color.
         */
        public Color getColor() {
            return mColor;
        }

        /**
         * Returns this shadows blending mode.
         */
        public BlendMode getBlendMode() {
            return mBlendMode;
        }
    }

    private List<Shadow> mOuterShadows = Collections.emptyList();
    private List<Shadow> mInnerShadows = Collections.emptyList();

    Effects() {
    }

    /**
     * Returns the list of inner shadows. Most of the time this
     * list will be empty or contain a single shadow.
     */
    public List<Shadow> getOuterShadows() {
        return Collections.unmodifiableList(mOuterShadows);
    }

    /**
     * Returns the list of inner shadows. Most of the time this
     * list will be empty or contain a single shadow.
     */
    public List<Shadow> getInnerShadows() {
        return Collections.unmodifiableList(mInnerShadows);
    }

    void addShadow(float blur, float angle, float distance, float opacity,
            Color color, BlendMode blendMode, boolean inner) {
        List<Shadow> list = inner ? mInnerShadows : mOuterShadows;
        if (list.size() == 0) {
            list = new ArrayList<Shadow>();
            if (inner) mInnerShadows = list;
            else mOuterShadows = list;
        }

        Shadow shadow = new Shadow(blur, angle, distance, opacity, color, blendMode);
        list.add(shadow);
    }
}
