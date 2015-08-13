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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.resources.Density;

import org.junit.Test;

import java.util.Set;

public class DensitySplitOptionsTest {

    @Test
    public void testDisabled() {
        DensitySplitOptions options = new DensitySplitOptions();

        Set<String> values = options.getApplicableFilters();

        assertEquals(0, values.size());
    }

    @Test
    public void testUniversal() {
        DensitySplitOptions options = new DensitySplitOptions();
        options.setEnable(true);

        Set<String> values = options.getApplicableFilters();
        // at this time we have 6 densities, maybe more later.
        assertTrue(values.size() >= 6);
    }

    @Test
    public void testNonDefaultInclude() {
        DensitySplitOptions options = new DensitySplitOptions();
        options.setEnable(true);

        options.include(Density.TV.getResourceValue());

        Set<String> values = options.getApplicableFilters();

        // test TV is showing up.
        assertTrue(values.contains(Density.TV.getResourceValue()));
        // test another default value also shows up
        assertTrue(values.contains(Density.HIGH.getResourceValue()));
    }

    @Test
    public void testUnallowedInclude() {
        DensitySplitOptions options = new DensitySplitOptions();
        options.setEnable(true);

        options.include(Density.ANYDPI.getResourceValue());

        Set<String> values = options.getApplicableFilters();

        // test ANYDPI isn't there.
        assertFalse(values.contains(Density.ANYDPI.getResourceValue()));

        // test another default value shows up
        assertTrue(values.contains(Density.XHIGH.getResourceValue()));
    }

    @Test
    public void testExclude() {
        DensitySplitOptions options = new DensitySplitOptions();
        options.setEnable(true);

        options.exclude(Density.XXHIGH.getResourceValue());

        Set<String> values = options.getApplicableFilters();

        assertFalse(values.contains(Density.XXHIGH.getResourceValue()));
    }
}