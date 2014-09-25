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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.SplitOutput;
import com.android.resources.Density;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SplitOutputMatcherTest extends TestCase {

    /**
     * Helper to run InstallHelper.computeMatchingOutput with variable ABI list.
     */
    private static SplitOutput computeBestOutput(
            @NonNull List<? extends SplitOutput> outputs,
            int density,
            @NonNull String... abis) {
        return SplitOutputMatcher.computeBestOutput(
                outputs, Collections.<String>emptySet(), density, Arrays.asList(abis));
    }

    private static SplitOutput computeBestOutput(
            @NonNull List<? extends SplitOutput> outputs,
            @NonNull Set<String> variantAbis,
            int density,
            @NonNull String... abis) {
        return SplitOutputMatcher.computeBestOutput(
                outputs, variantAbis, density, Arrays.asList(abis));
    }

    /**
     * Fake implementation of FilteredOutput
     */
    private static final class FakeSplitOutput implements SplitOutput {

        private final String densityFilter;
        private final String abiFilter;
        private final int versionCode;

        FakeSplitOutput(String densityFilter, String abiFilter, int versionCode) {
            this.densityFilter = densityFilter;
            this.abiFilter = abiFilter;
            this.versionCode = versionCode;
        }

        @Nullable
        @Override
        public String getDensityFilter() {
            return densityFilter;
        }

        @Nullable
        @Override
        public String getAbiFilter() {
            return abiFilter;
        }

        @NonNull
        @Override
        public File getOutputFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @Override
        public String toString() {
            return "FilteredOutput{" + densityFilter + ':' + abiFilter + ':' + versionCode + '}';
        }
    }

    public void testSingleOutput() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertEquals(match, result);
    }

    public void testDensityOnlyWithMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getDensityOutput(160, 2));
        list.add(getDensityOutput(320, 3));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertEquals(match, result);
    }

    public void testDensityOnlyWithUniversalMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(3));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 1));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertEquals(match, result);
    }

    public void testDensityOnlyWithNoMatch() {
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getDensityOutput(320, 1));
        list.add(getDensityOutput(480, 2));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertNull(result);
    }

    public void testDensityOnlyWithCustomDeviceDensity() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 3));

        SplitOutput result = computeBestOutput(list, 1, "foo");

        assertEquals(match, result);
    }


    public void testAbiOnlyWithMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertEquals(match, result);
    }

    public void testAbiOnlyWithMultiMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        SplitOutput result = computeBestOutput(list, 160, "bar", "foo");

        assertEquals(match, result);
    }

    public void testAbiOnlyWithMultiMatch2() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        // test where the versionCode does not match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        SplitOutput result = computeBestOutput(list, 160, "foo", "bar");

        assertEquals(match, result);
    }

    public void testAbiOnlyWithUniversalMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        SplitOutput result = computeBestOutput(list, 160, "zzz");

        assertEquals(match, result);
    }

    public void testAbiOnlyWithNoMatch() {
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("bar", 2));

        SplitOutput result = computeBestOutput(list, 160, "zzz");

        assertNull(result);
    }

    public void testMultiFilterWithMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getOutput(160, "zzz",2));
        list.add(match = getOutput(160, "foo", 4));
        list.add(getOutput(320, "foo", 3));

        SplitOutput result = computeBestOutput(list, 160, "foo");

        assertEquals(match, result);
    }

    public void testMultiFilterWithUniversalMatch() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(4));
        list.add(getOutput(320, "zzz", 3));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 1));

        SplitOutput result = computeBestOutput(list, 160, "zzz");

        assertEquals(match, result);
    }

    public void testMultiFilterWithNoMatch() {
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getOutput(320, "zzz", 1));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 3));

        SplitOutput result = computeBestOutput(list, 160, "zzz");

        assertNull(result);
    }

    public void testVariantLevelAbiFilter() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        SplitOutput result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "foo", "zzz");

        assertEquals(match, result);
    }

    public void testWrongVariantLevelAbiFilter() {
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));

        SplitOutput result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "zzz");

        assertNull(result);
    }

    public void testDensitySplitPlugVariantLevelAbiFilter() {
        SplitOutput match;
        List<SplitOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getDensityOutput(240, 2));
        list.add(match = getDensityOutput(320, 3));
        list.add(getDensityOutput(480, 4));

        SplitOutput result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 320, "foo", "zzz");

        assertEquals(match, result);
    }

    private static SplitOutput getUniversalOutput(int versionCode) {
        return new FakeSplitOutput(null, null, versionCode);
    }

    private static SplitOutput getDensityOutput(int densityFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeSplitOutput(densityEnum.getResourceValue(), null, versionCode);
    }

    private static SplitOutput getAbiOutput(String filter, int versionCode) {
        return new FakeSplitOutput( null, filter, versionCode);
    }

    private static SplitOutput getOutput(int densityFilter, String abiFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeSplitOutput(densityEnum.getResourceValue(), abiFilter, versionCode);
    }
}
