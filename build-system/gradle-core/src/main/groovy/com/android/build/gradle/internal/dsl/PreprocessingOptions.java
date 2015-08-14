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

package com.android.build.gradle.internal.dsl;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.resources.Density;
import com.google.common.collect.Sets;

import java.util.EnumSet;
import java.util.Set;

/**
 * Resource preprocessing options.
 */
public class PreprocessingOptions {
    private EnumSet<Density> densities;

    public PreprocessingOptions() {
        this.densities = EnumSet.noneOf(Density.class);
        for (Density density : Density.values()) {
            if (density.isRecommended()) {
                this.densities.add(density);
            }
        }
    }

    /**
     * Set of screen densities for which PNG files should be generated based on vector drawable
     * resource files.
     *
     * <p>Default to {@code ["mdpi", "hdpi", "xhdpi", "xxhdpi"]}.
     */
    public Set<String> getDensities() {
        Set<String> result = Sets.newHashSet();
        for (Density density : densities) {
            result.add(density.getResourceValue());
        }
        return result;
    }

    public void setDensities(Set<String> densities) {
        EnumSet<Density> newValue = EnumSet.noneOf(Density.class);
        for (String density : densities) {
            Density typedValue = Density.getEnum(density);
            checkArgument(typedValue != null, "Unrecognized density %s", density);
            newValue.add(typedValue);
        }
        this.densities = newValue;
    }

    /**
     * Returns the densities to generate as the underlying enum values.
     *
     * <p>Not meant to be used in build scripts.
     */
    public Set<Density> getTypedDensities() {
        return densities;
    }
}
