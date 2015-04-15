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

public class AbiSplitOptionsTest {

    @Test
    public void testDisabled() {
        AbiSplitOptions options = new AbiSplitOptions();

        Set<String> values = options.getApplicableFilters();

        assertEquals(1, values.size());
        assertTrue(values.contains(OutputFile.NO_FILTER));
    }

    @Test
    public void testNonUniversal() {
        AbiSplitOptions options = new AbiSplitOptions();
        options.setEnable(true);

        Set<String> values = options.getApplicableFilters();

        assertFalse(values.contains(OutputFile.NO_FILTER));
    }

    @Test
    public void testUniversal() {
        AbiSplitOptions options = new AbiSplitOptions();
        options.setEnable(true);
        options.setUniversalApk(true);

        Set<String> values = options.getApplicableFilters();

        assertTrue(values.contains(OutputFile.NO_FILTER));
    }

    @Test
    public void testUnallowedInclude() {
        AbiSplitOptions options = new AbiSplitOptions();
        options.setEnable(true);

        String wrongValue = "x86_126bit";
        options.include(wrongValue);

        Set<String> values = options.getApplicableFilters();

        // test wrong value isn't there.
        assertFalse(values.contains(wrongValue));

        // test another default value shows up
        assertTrue(values.contains("x86"));
    }

    @Test
    public void testExclude() {
        AbiSplitOptions options = new AbiSplitOptions();
        options.setEnable(true);

        String oldValue = "armeabi";
        options.exclude(oldValue);

        Set<String> values = options.getApplicableFilters();

        assertFalse(values.contains(oldValue));
    }
}