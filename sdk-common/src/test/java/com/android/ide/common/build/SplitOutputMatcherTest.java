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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.resources.Density;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SplitOutputMatcherTest extends TestCase {

    /**
     * Helper to run InstallHelper.computeMatchingOutput with variable ABI list.
     */
    private static List<OutputFile> computeBestOutput(
            @NonNull List<? extends VariantOutput> outputs,
            int density,
            @NonNull String... abis) {
        return SplitOutputMatcher.computeBestOutput(
                outputs, null, density, null /* language */, null /* region */,
                Arrays.asList(abis));
    }

    private static List<OutputFile> computeBestOutput(
            @NonNull List<? extends VariantOutput> outputs,
            @NonNull Set<String> variantAbis,
            int density,
            @NonNull String... abis) {
        return SplitOutputMatcher.computeBestOutput(
                outputs, variantAbis, density, null /* language */, null /* region */,
                Arrays.asList(abis));
    }

    private static List<OutputFile> computeBestOutput(
            @NonNull List<? extends VariantOutput> outputs,
            @NonNull Set<String> variantAbis,
            String language,
            String region,
            int density,
            @NonNull String... abis) {
        return SplitOutputMatcher.computeBestOutput(
                outputs, variantAbis, density, language, region, Arrays.asList(abis));
    }

    /**
     * Fake implementation of FilteredOutput
     */

    private static final class FakeSplitOutput implements OutputFile {

        private final String densityFilter;
        private final String abiFilter;
        private final File file;

        FakeSplitOutput(String densityFilter, String abiFilter) {
            this.densityFilter = densityFilter;
            this.abiFilter = abiFilter;
            file = new File(densityFilter + abiFilter);
        }

        @Override
        public String getOutputType() {
            return OutputFile.FULL_SPLIT;
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            ImmutableList.Builder<String> splitTypeBuilder = ImmutableList.builder();
            if (densityFilter != null) {
                splitTypeBuilder.add(OutputFile.DENSITY);
            }
            if (abiFilter != null) {
                splitTypeBuilder.add(OutputFile.ABI);
            }
            return splitTypeBuilder.build();
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            ImmutableList.Builder<FilterData> filters = ImmutableList.builder();
            if (densityFilter != null) {
                filters.add(FakeFilterData.Builder.build(OutputFile.DENSITY, densityFilter));
            }
            if (abiFilter != null) {
                filters.add(FakeFilterData.Builder.build(OutputFile.ABI, abiFilter));
            }
            return filters.build();
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return file;
        }

        @Override
        public String toString() {
            return "FilteredOutput{" + densityFilter + ':' + abiFilter + '}';
        }
    }

    private static final class FakeFilterData implements FilterData {
        private final String filterType;
        private final String identifier;

        FakeFilterData(String filterType, String identifier) {
            this.filterType = filterType;
            this.identifier = identifier;
        }

        @NonNull
        @Override
        public String getIdentifier() {
            return identifier;
        }

        @NonNull
        @Override
        public String getFilterType() {
            return filterType;
        }

        public static class Builder {
            public static FilterData build(final String filterType, final String identifier) {
                return new FakeFilterData(filterType, identifier);
            }
        }
    }

    private static final class FakeMainOutputFile implements OutputFile {

        @NonNull
        @Override
        public String getOutputType() {
            return OutputType.MAIN.name();
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            return ImmutableList.of();
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            return ImmutableList.of();
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return new File("MAIN");
        }
    }

    private static final class FakePureSplitOutput implements OutputFile {
        private final String filter;
        private final OutputFile.FilterType filterType;
        private final File file;


        private FakePureSplitOutput(String filter, FilterType filterType) {
            this.filter = filter;
            this.filterType = filterType;
            this.file = new File(filterType.name() + filter);
        }

        @NonNull
        @Override
        public String getOutputType() {
            return OutputType.SPLIT.name();
        }

        @NonNull
        @Override
        public Collection<String> getFilterTypes() {
            return ImmutableList.of(filterType.name());
        }

        @NonNull
        @Override
        public Collection<FilterData> getFilters() {
            return ImmutableList.of(FilterData.Builder.build(filterType.name(), filter));
        }

        @NonNull
        @Override
        public File getOutputFile() {
            return file;
        }
    }

    private static class FakeVariantOutput implements VariantOutput {

        private final OutputFile mainOutputFile;
        private final int versionCode;

        private FakeVariantOutput(OutputFile mainOutputFile,
                int versionCode) {
            this.mainOutputFile = mainOutputFile;
            this.versionCode = versionCode;
        }

        @NonNull
        @Override
        public OutputFile getMainOutputFile() {
            return mainOutputFile;
        }

        @NonNull
        @Override
        public Collection<? extends OutputFile> getOutputs() {
            return ImmutableList.of(mainOutputFile);
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @NonNull
        @Override
        public File getSplitFolder() {
            return null;
        }
    }

    public static class FakePureSplitsVariantOutput extends FakeVariantOutput {
        private final ImmutableList<? extends OutputFile> pureSplitOutputFiles;

        public FakePureSplitsVariantOutput(
                OutputFile mainOutputFile,
                ImmutableList<? extends OutputFile> pureSplitOutputFiles,
                int versionCode) {
            super(mainOutputFile, versionCode);
            this.pureSplitOutputFiles = pureSplitOutputFiles;
        }

        @NonNull
        @Override
        public Collection<? extends OutputFile> getOutputs() {
            ImmutableList.Builder<OutputFile> outputFileBuilder = ImmutableList.builder();
            outputFileBuilder.add(getMainOutputFile());
            outputFileBuilder.addAll(pureSplitOutputFiles);
            return outputFileBuilder.build();
        }
    }

    public void testSingleOutput() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));

        List<OutputFile> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getDensityOutput(160, 2));
        list.add(getDensityOutput(320, 3));

        List<OutputFile> result =  computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithUniversalMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(3));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 1));

        List<OutputFile> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testDensityOnlyWithNoMatch() {
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getDensityOutput(320, 1));
        list.add(getDensityOutput(480, 2));

        List<OutputFile> result = computeBestOutput(list, 160, "foo");

        assertEquals(0, result.size());
    }

    public void testDensityOnlyWithCustomDeviceDensity() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getDensityOutput(320, 2));
        list.add(getDensityOutput(480, 3));

        List<OutputFile> result = computeBestOutput(list, 1, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }


    public void testAbiOnlyWithMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(match = getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<OutputFile> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<OutputFile> result = computeBestOutput(list, 160, "bar", "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithMultiMatch2() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        // test where the versionCode does not match the abi order
        list.add(getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(match = getAbiOutput("bar", 3));

        // bar is preferred over foo
        List<OutputFile> result = computeBestOutput(list, 160, "foo", "bar");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithUniversalMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        list.add(getAbiOutput("foo", 2));
        list.add(getAbiOutput("bar", 3));

        List<OutputFile> result = computeBestOutput(list, 160, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testAbiOnlyWithNoMatch() {
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getAbiOutput("foo", 1));
        list.add(getAbiOutput("bar", 2));

        List<OutputFile> result = computeBestOutput(list, 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testMultiFilterWithMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getOutput(160, "zzz",2));
        list.add(match = getOutput(160, "foo", 4));
        list.add(getOutput(320, "foo", 3));

        List<OutputFile> result = computeBestOutput(list, 160, "foo");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testMultiFilterWithUniversalMatch() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(4));
        list.add(getOutput(320, "zzz", 3));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 1));

        List<OutputFile> result = computeBestOutput(list, 160, "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testMultiFilterWithNoMatch() {
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getOutput(320, "zzz", 1));
        list.add(getOutput(160, "bar", 2));
        list.add(getOutput(320, "foo", 3));

        List<OutputFile> result = computeBestOutput(list, 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testVariantLevelAbiFilter() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(match = getUniversalOutput(1));
        List<OutputFile> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "foo",
                "zzz");

        assertEquals(1, result.size());
        assertEquals(match.getMainOutputFile(), result.get(0));
    }

    public void testWrongVariantLevelAbiFilter() {
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));

        List<OutputFile> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 160, "zzz");

        assertEquals(0, result.size());
    }

    public void testDensitySplitPlugVariantLevelAbiFilter() {
        VariantOutput match;
        List<VariantOutput> list = Lists.newArrayList();

        list.add(getUniversalOutput(1));
        list.add(getDensityOutput(240, 2));
        list.add(match = getDensityOutput(320, 3));
        list.add(getDensityOutput(480, 4));

        List<OutputFile> result = computeBestOutput(list, Sets.newHashSet("bar", "foo"), 320, "foo", "zzz");

        assertEquals(1, result.size());
    }

    public void testCombinedDensitySplitAndLanguageSplit() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 320,  "foo");
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testCombinedDensitySplitAndRegionalLanguageSplit() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_CA", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_FR", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 320,  "foo");
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testCombinedDensitySplitAndRegionalLanguageSplit2() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_CA", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_FR", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", "CA", 320,  "foo");
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr_CA");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testCombinedDensitySplitAndMissingRegionalLanguageSplit() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_CA", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_FR", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", "BE", 320,  "foo");
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        // no belgium specific resources, we should revert to french...
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testCombinedDensitySplitAndMissingRegionalLanguageSplit2() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("fr_CH", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_CA", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("fr_FR", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", "BE", 320,  "foo");
        assertEquals(2, results.size());
        assertMainOutputFilePresence(results);
        // no belgium specific resources, and no generic french either.. so no french resources
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testCombinedDensitySplitAndMissingLanguageSplit() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("de", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 320,  "foo");
        // no french resources found.
        assertEquals(2, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
    }

    public void testAllCombinedPureSplits() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("foo", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("bar", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 320,  "foo");
        assertEquals(4, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.ABI, "foo");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
    }

    public void testMissingABIPureSplits() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("foo", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("bar", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 320,  "baz");
        // no "baz" ABI split found
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.DENSITY,
                Density.getEnum(320).getResourceValue());
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
    }

    public void testMissingDensityPureSplit() {
        VariantOutput match;
        List<VariantOutput> list = new ArrayList<VariantOutput>();

        match = new FakePureSplitsVariantOutput(
                new FakeMainOutputFile(),
                ImmutableList.of(
                        new FakePureSplitOutput(Density.getEnum(320).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(480).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput(Density.getEnum(240).getResourceValue(),
                                OutputFile.FilterType.DENSITY),
                        new FakePureSplitOutput("foo", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("bar", OutputFile.FilterType.ABI),
                        new FakePureSplitOutput("fr", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("es", OutputFile.FilterType.LANGUAGE),
                        new FakePureSplitOutput("it", OutputFile.FilterType.LANGUAGE)),
                1);
        list.add(match);
        List<OutputFile> results = computeBestOutput(
                list, ImmutableSet.<String>of(), "fr", null /*region */, 640,  "foo");

        // missing density ABI.
        assertEquals(3, results.size());
        assertMainOutputFilePresence(results);
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.ABI, "foo");
        assertPureSplitOutputFilePresence(results, OutputFile.FilterType.LANGUAGE, "fr");
    }

    private void assertMainOutputFilePresence(List<OutputFile> outputFiles) {
        for (OutputFile outputFile : outputFiles) {
            if (outputFile.getOutputType().equals(OutputFile.OutputType.MAIN.name())) {
                return;
            }
        }
        fail("Cannot find main OutputFile");
    }

    private void assertPureSplitOutputFilePresence(List<OutputFile> outputFiles,
            @NonNull OutputFile.FilterType filterType,
            @NonNull String filter) {
        for (OutputFile outputFile : outputFiles) {
            if (outputFile.getOutputType().equals(OutputFile.OutputType.SPLIT.name())
                    && outputFile.getFilterTypes().size() == 1
                    && outputFile.getFilterTypes().contains(filterType.name())
                    && outputFile.getFilters().size() == 1
                    && outputFile.getFilters().iterator().next().getIdentifier().equals(filter)) {
                return;

            }
        }
        fail("Cannot find pure split " + filterType + " : " + filter);
    }


    private static VariantOutput getUniversalOutput(int versionCode) {
        return new FakeVariantOutput(new FakeSplitOutput(null, null), versionCode);
    }

    private static VariantOutput getDensityOutput(int densityFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeVariantOutput(
                new FakeSplitOutput(densityEnum.getResourceValue(), null), versionCode);
    }

    private static VariantOutput getAbiOutput(String filter, int versionCode) {
        return new FakeVariantOutput(
                new FakeSplitOutput( null, filter), versionCode);
    }

    private static VariantOutput getOutput(int densityFilter, String abiFilter, int versionCode) {
        Density densityEnum = Density.getEnum(densityFilter);
        return new FakeVariantOutput(
                new FakeSplitOutput(densityEnum.getResourceValue(), abiFilter), versionCode);
    }
}
