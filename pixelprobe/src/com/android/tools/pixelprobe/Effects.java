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

import com.android.tools.pixelprobe.effect.Shadow;
import com.android.tools.pixelprobe.util.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the effects associated with an image's layer.
 */
public final class Effects {
    private final List<Shadow> outerShadows;
    private final List<Shadow> innerShadows;

    Effects(Builder builder) {
        outerShadows = Lists.immutableCopy(builder.outerShadows);
        innerShadows = Lists.immutableCopy(builder.innerShadows);
    }

    /**
     * Returns the list of inner shadows. Most of the time this
     * list will be empty or contain a single shadow.
     */
    public List<Shadow> getOuterShadows() {
        return Collections.unmodifiableList(outerShadows);
    }

    /**
     * Returns the list of inner shadows. Most of the time this
     * list will be empty or contain a single shadow.
     */
    public List<Shadow> getInnerShadows() {
        return Collections.unmodifiableList(innerShadows);
    }

    public static final class Builder {
        List<Shadow> outerShadows = new ArrayList<>();
        List<Shadow> innerShadows = new ArrayList<>();

        public Builder addShadow(Shadow shadow) {
            boolean isInner = shadow.getType() == Shadow.Type.INNER;
            List<Shadow> list = isInner ? innerShadows : outerShadows;
            if (list.size() == 0) {
                list = new ArrayList<>();
                if (isInner) innerShadows = list;
                else outerShadows = list;
            }

            list.add(shadow);
            return this;
        }

        public Effects build() {
            return new Effects(this);
        }
    }
}
