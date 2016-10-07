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

package com.android.builder.internal.packaging.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Stopwatch;

import org.junit.Ignore;
import org.junit.Test;

import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link FileUseMap}.
 */
public class FileUseMapTest {

    /**
     * Verifies that as elements are added to the map, the performance of adding new elements
     * is not significantly downgraded. This test creates a map and does several runs until
     * a maximum is reached or a time limit is reached.
     *
     * <p>In each run, a random block is requested from the map with a random alignment and offset.
     * The time for each run is saved.
     *
     * <p>After all runs are completed, the average time of the first runs (the head time) and
     * the average time of the last runs (the tail time) is computed, as well as the average
     * time.
     *
     * <p>The test passes if the average tail set time is (1) at most twice as long as the average
     * and (2) is at most three times as long as the head set. This ensures that performance can
     * degrade somewhat as the file map size increases, but not too much.
     */
    @Test
    @Ignore("This test relies on magic ratios to detect when performance is bad.")
    public void addPerformanceTest() {
        final long MAP_SIZE = 10000000;
        final int MAX_RUNS = 10000;
        final long MAX_TEST_DURATION_MS = 1000;
        final int MAX_RANDOM_BLOCK_SIZE = 1000;
        final int MAX_RANDOM_ALIGNMENT = 10;
        final int HEAD_SET_SIZE = 1000;
        final int TAIL_SET_SIZE = 1000;
        final double MAX_TAIL_HEAD_RATIO = 3.0;
        final double MAX_TAIL_TOTAL_RATIO = 2.0;

        long mapSize = MAP_SIZE;
        FileUseMap map = new FileUseMap(mapSize, 0);
        Random rand = new Random(0);

        long[] runs = new long[MAX_RUNS];
        int currentRun = 0;

        Stopwatch testStopwatch = Stopwatch.createStarted();
        while (testStopwatch.elapsed(TimeUnit.MILLISECONDS) < MAX_TEST_DURATION_MS
                && currentRun < runs.length) {
            Stopwatch runStopwatch = Stopwatch.createStarted();

            long blockSize = 1 + rand.nextInt(MAX_RANDOM_BLOCK_SIZE);
            long start = map.locateFree(blockSize, rand.nextInt(MAX_RANDOM_ALIGNMENT),
                    rand.nextInt(MAX_RANDOM_ALIGNMENT), FileUseMap.PositionAlgorithm.BEST_FIT);
            long end = start + blockSize;
            if (end >= mapSize) {
                mapSize *= 2;
                map.extend(mapSize);
            }

            map.add(start, end, new Object());

            runs[currentRun] = runStopwatch.elapsed(TimeUnit.NANOSECONDS);
            currentRun++;
        }

        double initialAvg = 0;
        for (int i = 0; i < HEAD_SET_SIZE; i++) {
            initialAvg += runs[i];
        }

        initialAvg /= HEAD_SET_SIZE;

        double endAvg = 0;
        for (int i = currentRun - TAIL_SET_SIZE; i < currentRun; i++) {
            endAvg += runs[i];
        }

        endAvg /= TAIL_SET_SIZE;

        double totalAvg = 0;
        for (int i = 0; i < runs.length; i++) {
            totalAvg += runs[i];
        }

        totalAvg /= currentRun;

        if (endAvg > totalAvg * MAX_TAIL_TOTAL_RATIO || endAvg > initialAvg * MAX_TAIL_HEAD_RATIO) {
            DecimalFormat df = new DecimalFormat("#,###");

            fail("Add performance at end is too bad. Performance in the beginning is "
                    + df.format(initialAvg) + "ns per insertion and at the end is "
                    + df.format(endAvg) + "ns. Average over the total of " + currentRun + " runs "
                    + "is " + df.format(totalAvg) + "ns.");
        }
    }

    @Test
    public void testSizeComputation() {
        FileUseMap m = new FileUseMap(200, 0);

        assertEquals(200, m.size());
        assertEquals(0, m.usedSize());

        m.add(10, 20, new Object());
        assertEquals(200, m.size());
        assertEquals(20, m.usedSize());
    }
}
