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

package com.android.build.gradle.internal.publishing;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.model.FilterDataImpl;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.SplitFileSupplier;
import com.google.common.collect.ImmutableList;

import org.gradle.api.Task;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

/**
 * Tests for the {@kink FilterDataPersistence}
 */
public class FilterDataPersistenceTest {

    FilterDataPersistence persistence = new FilterDataPersistence();
    StringWriter writer = new StringWriter();

    @Test
    public void testPersistedFormat() throws IOException {
        persistence.persist(
                ImmutableList.<FileSupplier>of(
                        makeFileSupplier(OutputFile.FilterType.DENSITY, "xxhdpi")),
                writer);

        assertEquals("[{\"filterType\":\"DENSITY\",\"filterIdentifier\":\"xxhdpi\",\"splitFileName\":\"DENSITY_xxhdpi\"}]",
                writer.toString());
    }

    @Test
    public void testPersistence() throws IOException {

        persistence.persist(
                ImmutableList.<FileSupplier>of(
                        makeFileSupplier(OutputFile.FilterType.DENSITY, "xxhdpi")),
                writer);
        List<FilterDataPersistence.Record> loadedRecords = persistence
                .load(new StringReader(writer.toString()));
        assertEquals(1, loadedRecords.size());
        assertEquals(OutputFile.FilterType.DENSITY.name(), loadedRecords.get(0).filterType);
        assertEquals("xxhdpi", loadedRecords.get(0).filterIdentifier);
        assertEquals(OutputFile.DENSITY + "_" + "xxhdpi", loadedRecords.get(0).splitFileName);
    }

    @Test
    public void testMultiplePersistence() throws IOException {

        persistence.persist(
                ImmutableList.<FileSupplier>of(
                        makeFileSupplier(OutputFile.FilterType.DENSITY, "xxhdpi"),
                        makeFileSupplier(OutputFile.FilterType.ABI, "arm"),
                        makeFileSupplier(OutputFile.FilterType.LANGUAGE, "fr")),
                writer);
        List<FilterDataPersistence.Record> loadedRecords = persistence
                .load(new StringReader(writer.toString()));
        assertEquals(3, loadedRecords.size());
    }

    private SplitFileSupplier makeFileSupplier(final OutputFile.FilterType filterType,
            final String filterId) {

        return new SplitFileSupplier() {
            @NonNull
            @Override
            public FilterData getFilterData() {
                return FilterDataImpl.build(filterType.name(), filterId);
            }

            @NonNull
            @Override
            public Task getTask() {
                return Mockito.mock(Task.class);
            }

            @Override
            public File get() {
                return new File(filterType.name() + "_" + filterId);
            }
        };
    }
}
