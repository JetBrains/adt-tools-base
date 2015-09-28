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

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;

import java.util.*;

/**
 * A group of streams of data sampled over time. This object is thread safe as it can be
 * read/modified from any thread. It uses itself as the mutex object so it is possible to
 * synchronize on it if modifications from other threads want to be prevented.
 */
public class TimelineData {

    private final int myStreams;

    @GuardedBy("this")
    private final List<Sample> mSamples;

    @GuardedBy("this")
    private long mStart;

    // The highest value across all streams being stacked together.
    @GuardedBy("this")
    private float mMaxTotal;

    // The lowest value across all streams being stacked together.
    @GuardedBy("this")
    private float mMinTotal;

    // The highest value of any single stream.
    @GuardedBy("this")
    private float mStreamMax;

    // The lowest value of any single stream.
    @GuardedBy("this")
    private float mStreamMin;

    public TimelineData(int streams, int capacity) {
        myStreams = streams;
        mSamples = new CircularArrayList<Sample>(capacity);
        clear();
    }

    @VisibleForTesting
    public synchronized long getStartTime() {
        return mStart;
    }

    public int getStreamCount() {
        return myStreams;
    }

    public synchronized float getMaxTotal() {
        return mMaxTotal;
    }


    public synchronized  float getMinTotal() {
        return mMinTotal;
    }

    public synchronized float getStreamMax() {
        return mStreamMax;
    }

    public synchronized float getStreamMin() {
        return mStreamMin;
    }

    public synchronized void add(long time, int type, float... values) {
        add(new Sample((time - mStart) / 1000.0f, type, values));
    }

    /**
     * Converts stream area values to stream samples. All streams have different starting values from last sample, different areas,
     * and result in different sample shapes. The conversion may break into multiple samples time points to make the shapes' areas are
     * correct. For example, every stream flow is a triangle when not stacked with each other; it need four time points for all streams,
     * one triangle is split into four parts at every time point, each part's shape may be changed while the area size is the same.
     *
     * @param time The current time in seconds from the start timestamp.
     * @param areas The streams' area sizes.
     * @param lastSample The last recent sample, which may be null.
     * @param type The timeline data type.
     */
    private static List<Sample> convertAreasToSamples(float time, int type, float[] areas, @Nullable Sample lastSample) {
        int streamSize = areas.length;
        // The starting time and value are from last sample, to be consecutive.
        float startTime = lastSample != null ? lastSample.time : 0.0f;
        float[] startValues = lastSample != null ? lastSample.values : new float[streamSize];
        assert streamSize == startValues.length;

        // Computes how long every stream's value is non-zero and the ending value at last.
        float maxInterval = time - startTime;
        if (maxInterval <= 0) {
            return new ArrayList<Sample>();
        }
        float[] nonZeroIntervalsForStreams = new float[streamSize];
        float[] endValuesForStreams = new float[streamSize];
        for (int i = 0; i < streamSize; i++) {
            if (Math.abs(startValues[i]) * maxInterval / 2 < Math.abs(areas[i])) {
                nonZeroIntervalsForStreams[i] = maxInterval;
                endValuesForStreams[i] = areas[i] * 2 / maxInterval - startValues[i];
            }
            else if (areas[i] == 0) {
                nonZeroIntervalsForStreams[i] = maxInterval;
                endValuesForStreams[i] = 0;
            }
            else {
                // startValues[i] should be non-zero to be greater than areas[i].
                nonZeroIntervalsForStreams[i] = areas[i] * 2 / startValues[i];
                endValuesForStreams[i] = 0;
            }
        }

        // Sorts the intervals, every different interval should be a sample.
        float[] ascendingIntervals = Arrays.copyOf(nonZeroIntervalsForStreams, streamSize);
        Arrays.sort(ascendingIntervals);
        List<Sample> sampleList = new ArrayList<Sample>();
        for (float interval : ascendingIntervals) {
            float[] sampleValues = new float[streamSize];
            for (int j = 0; j < streamSize; j++) {
                sampleValues[j] = nonZeroIntervalsForStreams[j] < interval
                                  ? 0.0f
                                  : startValues[j] - (startValues[j] - endValuesForStreams[j]) * interval / nonZeroIntervalsForStreams[j];
            }
            sampleList.add(new Sample(interval + startTime, type, sampleValues));
            if (interval == maxInterval) {
                break;
            }
        }
        if (ascendingIntervals[streamSize - 1] < maxInterval) {
            // Adds the ending sample that all stream values are zero.
            sampleList.add(new Sample(time, type, new float[streamSize]));
        }
        return sampleList;
    }

    /**
     * Adds the stream values which are values converted from the areas values. The values depends on both last sample values and
     * the current areas' sizes. It should be a synchronized method to let the last recent sample be accurate.
     *
     * @param timeMills The current time in mills.
     * @param type Sample data type.
     * @param areas Value multiple time area sizes for all streams.
     */
    public synchronized void addFromArea(long timeMills, int type, float... areas) {
        float timeForStart = (timeMills - mStart) / 1000.0f;
        Sample lastSample = mSamples.isEmpty() ? null : mSamples.get(mSamples.size() - 1);
        for (Sample sample : convertAreasToSamples(timeForStart, type, areas, lastSample)) {
            add(sample);
        }
    }

    private void add(Sample sample) {
        float[] values = sample.values;
        assert values.length == myStreams;
        float stacked = 0.0f;
        for (float value : values) {
            stacked += value;
            mMaxTotal = Math.max(mMaxTotal, stacked);
            mMinTotal = Math.min(mMinTotal, stacked);
            mStreamMax = Math.max(mStreamMax, value);
            mStreamMin = Math.min(mStreamMin, value);
        }
        mSamples.add(sample);
    }

    public synchronized void clear() {
        mSamples.clear();
        mMaxTotal = 0.0f;
        mStreamMax = 0.0f;
        mStart = System.currentTimeMillis();
    }

    public int size() {
        return mSamples.size();
    }

    public Sample get(int index) {
        return mSamples.get(index);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public synchronized float getEndTime() {
        return (mSamples.isEmpty() ? 0.0f : (System.currentTimeMillis() - mStart)) / 1000.f;
    }

    /**
     * A sample of all the streams at a given moment in time.
     */
    public static class Sample {

        /**
         * The time of the sample. In seconds since the start of the sampling.
         */
        public final float time;

        public final float[] values;

        public final int type;

        public Sample(float time, int type, float[] values) {
            this.time = time;
            this.values = values;
            this.type = type;
        }
    }
}
