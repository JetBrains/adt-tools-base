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
package com.android.tools.chartlib;

import junit.framework.TestCase;

public class TimelineDataTest extends TestCase {

    private static final int TYPE_DATA = 0;

    private TimelineData mData;

    private long mCreationTime;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCreationTime = System.currentTimeMillis();
        mData = new TimelineData(2, 2);
    }

    public void testStreamGetters() throws Exception {
        assertEquals(2, mData.getStreamCount());
    }

    public void testGetStartTime() throws Exception {
        assertTrue(mCreationTime <= mData.getStartTime());
        assertTrue(mData.getStartTime() <= System.currentTimeMillis());
        Thread.sleep(10);
        long now = System.currentTimeMillis();
        mData.clear();
        assertTrue(now <= mData.getStartTime());
    }

    public void testGetMaxTotal() throws Exception {
        assertEquals(0.0f, mData.getMaxTotal());
        long now = System.currentTimeMillis();
        mData.add(now + 1, 0, 1.0f, 2.0f);
        assertEquals(3.0f, mData.getMaxTotal());
        mData.add(now + 2, 0, 1.0f, 1.0f);
        assertEquals(3.0f, mData.getMaxTotal());
        mData.add(now + 3, 0, 2.0f, 2.0f);
        assertEquals(4.0f, mData.getMaxTotal());
    }

    public void testAdd() throws Exception {
        assertEquals(0, mData.size());
        long start = mData.getStartTime();

        mData.add(start, 0, 1.0f, 2.0f);
        assertEquals(1, mData.size());
        assertEquals(0.0f, mData.get(0).time, 0.0001f);
        assertEquals(1.0f, mData.get(0).values[0]);
        assertEquals(2.0f, mData.get(0).values[1]);

        mData.add(start + 1000, 0, 3.0f, 4.0f);
        assertEquals(2, mData.size());
        assertEquals(1.0f, mData.get(1).time, 0.0001f);
        assertEquals(3.0f, mData.get(1).values[0]);
        assertEquals(4.0f, mData.get(1).values[1]);

        mData.add(start + 2000, 0, 5.0f, 6.0f);
        assertEquals(2, mData.size());
        assertEquals(2.0f, mData.get(1).time, 0.0001);
        assertEquals(5.0f, mData.get(1).values[0]);
        assertEquals(6.0f, mData.get(1).values[1]);

        mData.clear();
        assertEquals(0, mData.size());
    }

    public void testAddFromAreaForFirstSample() {
        assertEquals(0, mData.size());
        long start = mData.getStartTime();
        mData.addFromArea(start + 1000, TYPE_DATA, 500.0f, 100.0f);
        assertEquals(1, mData.size());
        TimelineData.Sample sample = mData.get(0);
        assertEquals(1.0f, sample.time);
        assertEquals(1000.0f, sample.values[0]);
        assertEquals(200.0f, sample.values[1]);
    }

    public void testAddFromAreaExpectSpeedNotGoingDown() {
        assertEquals(0, mData.size());
        long start = mData.getStartTime();
        mData.addFromArea(start + 1000, TYPE_DATA, 500.0f, 100.0f);
        mData.addFromArea(start + 1500, TYPE_DATA, 500.0f, 100.0f);
        assertEquals(2, mData.size());
        TimelineData.Sample sample = mData.get(1);
        assertEquals(1.5f, sample.time);
        assertEquals(1000.0f, sample.values[0]);
        assertEquals(200.0f, sample.values[1]);
    }

    public void testAddFromAreaExpectFirstStreamSpeedGoingToZeroFirst() {
        mData = new TimelineData(2, 4);
        assertEquals(0, mData.size());
        long start = mData.getStartTime();
        mData.addFromArea(start + 1000, TYPE_DATA, 500.0f, 100.0f);
        mData.addFromArea(start + 2000, TYPE_DATA, 250.0f, 75.0f);
        assertEquals(4, mData.size());
        TimelineData.Sample sample = mData.get(1);
        assertEquals(1.5f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(66.66667f, sample.values[1]);
        sample = mData.get(2);
        assertEquals(1.75f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
        sample = mData.get(3);
        assertEquals(2.0f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
    }

    public void testAddFromAreaExpectSecondStreamSpeedGoingToZeroFirst() {
        mData = new TimelineData(2, 4);
        assertEquals(0, mData.size());
        long start = mData.getStartTime();
        mData.addFromArea(start + 1000, TYPE_DATA, 500.0f, 100.0f);
        mData.addFromArea(start + 2000, TYPE_DATA, 400.0f, 50.0f);
        assertEquals(4, mData.size());
        TimelineData.Sample sample = mData.get(1);
        assertEquals(1.5f, sample.time);
        assertEquals(375.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
        sample = mData.get(2);
        assertEquals(1.8f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
        sample = mData.get(3);
        assertEquals(2.0f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
    }

    public void testAddFromAreaWithZeroAreas() {
        assertEquals(0, mData.size());
        long start = mData.getStartTime();
        mData.addFromArea(start + 1000, TYPE_DATA, 500.0f, 100.0f);
        mData.addFromArea(start + 2000, TYPE_DATA, 0.0f, 0.0f);
        assertEquals(2, mData.size());
        TimelineData.Sample sample = mData.get(1);
        assertEquals(2.0f, sample.time);
        assertEquals(0.0f, sample.values[0]);
        assertEquals(0.0f, sample.values[1]);
    }
}
