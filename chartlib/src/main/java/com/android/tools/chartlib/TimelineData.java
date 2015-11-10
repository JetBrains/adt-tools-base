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

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;

import java.util.*;
import java.util.logging.Logger;

/**
 * A group of streams of data sampled over time. This object is thread safe as it can be
 * read/modified from any thread. It uses itself as the mutex object so it is possible to
 * synchronize on it if modifications from other threads want to be prevented.
 */
public class TimelineData {

    public static final Logger LOG = Logger.getLogger(TimelineData.class.getName());

    @GuardedBy("this")
    private long mStart;

    // Streams' id and values.
    public final List<Stream> mStreams;

    // Information related to sampling, for example sample time, sample type.
    private final List<SampleInfo> mSampleInfos;

    private final int mCapacity;

    // TODO: The streams parameter may not be needed, improve stream initial set up.
    public TimelineData(int streams, int capacity) {
        mCapacity = capacity;
        mSampleInfos = new CircularArrayList<SampleInfo>(capacity);
        mStreams = new ArrayList<Stream>();
        addDefaultStreams(streams);
        clear();
    }

    private void addDefaultStreams(int streams) {
        for (int i = 0; i < streams; i++) {
            addStream("Stream " + i);
        }
    }

    public synchronized long getStartTime() {
        return mStart;
    }

    public int getStreamCount() {
        return mStreams.size();
    }

    public Stream getStream(int index) {
        return mStreams.get(index);
    }

    public SampleInfo getSampleInfo(int index) {
        return mSampleInfos.get(index);
    }

    public synchronized void add(long time, int type, float... values) {
        add((time - mStart) / 1000.0f, type, values);
    }

    private synchronized void add(float timeFromStart, int type, float[] values) {
        mSampleInfos.add(new SampleInfo(timeFromStart, type));
        int valueLength = values.length;
        assert valueLength == getStreamCount();
        for (int i = 0; i < valueLength; i++) {
            mStreams.get(i).add(values[i]);
        }
    }

    /**
     * Converts stream area values to stream samples. All streams have different starting values from last sample, different areas,
     * and result in different sample shapes. The conversion may break into multiple samples time points to make the shapes' areas are
     * correct. For example, every stream flow is a triangle when not stacked with each other; it need four time points for all streams,
     * one triangle is split into four parts at every time point, each part's shape may be changed while the area size is the same.
     *
     * <p>Because both the sample time and stream values are needed to return, Sample class is kept to be used in the return value
     * until that class is removed. </p>
     *
     * @param time The current time in seconds from the start timestamp.
     * @param type The timeline data type.
     * @param areas The streams' area sizes.
     * @param startTime The time in seconds of the latest existing sample.
     * @param startValues Each stream's start value for new sample values' calculation.
     */
    private static List<Sample> convertAreasToSamples(float time, int type, float[] areas, float startTime, float[] startValues) {
        int streamSize = areas.length;
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
        float latestSampleTime = mSampleInfos.size() > 0 ? mSampleInfos.get(mSampleInfos.size() - 1).time : 0;
        float[] startValues = new float[areas.length];
        if (latestSampleTime > 0) {
            for (int i = 0; i < mStreams.size(); i++) {
                Stream stream = mStreams.get(i);
                startValues[i] = stream.get(stream.getValueSize() - 1);
            }
        }
        for (Sample sample : convertAreasToSamples(timeForStart, type, areas, latestSampleTime, startValues)) {
            add(sample.time, type, sample.values);
        }
    }

    public synchronized void addStream(@NonNull String id) {
        for (Stream stream : mStreams) {
            assert !id.equals(stream.getId()) : String.format("Attempt to add duplicate stream of id %1$s", id);
        }
        int startSize = mSampleInfos.size();
        Stream stream = new Stream(id, mCapacity, startSize);
        mStreams.add(stream);
    }

    public synchronized void addStreams(@NonNull List<String> ids) {
        for (String id : ids) {
            addStream(id);
        }
    }

    public synchronized void removeStream(@NonNull String id) {
        for (Stream stream : mStreams) {
            if (id.equals(stream.mId)) {
                mStreams.remove(stream);
                return;
            }
        }
        LOG.warning(String.format("Attempt to remove non-existing stream with id %1$s", id));
    }

    public synchronized void removeStreams(@NonNull List<String> ids) {
        for (String id : ids) {
            removeStream(id);
        }
    }

    public synchronized void clear() {
        mSampleInfos.clear();
        for (Stream stream : mStreams) {
            stream.reset();
        }
        mStart = System.currentTimeMillis();
    }

    public int size() {
        return mSampleInfos.size();
    }

    /**
     * @Deprecated.
     * TODO: Remove all usages then remove this method.
     */
    public Sample get(int index) {
        SampleInfo info = mSampleInfos.get(index);
        float[] values = new float[mStreams.size()];
        for (int i = 0; i < mStreams.size(); i++) {
            values[i] = mStreams.get(i).get(index);
        }
        return new Sample(info.time, info.type, values);
    }

    public synchronized float getEndTime() {
        return size() > 0 ? (System.currentTimeMillis() - mStart) / 1000.f : 0.0f;
    }

    public static class Stream {

        public final String mId;

        public final float[] mCircularValues;

        private int mStartIndex;

        private int mValueSize;

        public Stream(@NonNull String id, int maxValueSize, int startSize) {
            mId = id;
            mCircularValues = new float[maxValueSize];
            mStartIndex = 0;
            mValueSize = startSize;
        }

        public int getValueSize() {
            return mValueSize;
        }

        public void add(float value) {
            if (mValueSize == mCircularValues.length) {
                mCircularValues[mStartIndex] = value;
                mStartIndex = (mStartIndex + 1) % mValueSize;
            }
            else {
                mCircularValues[mValueSize] = value;
                mValueSize++;
            }
        }

        public String getId() {
            return mId;
        }

        public float get(int index) {
            assert index >= 0 && index < mValueSize : String.format("Index %1$d out of value length bound %2$d", index, mValueSize);
            return mCircularValues[(mStartIndex + index) % mValueSize];
        }

        public void reset() {
            mStartIndex = 0;
            mValueSize = 0;
        }
    }

    public static class SampleInfo {

        public final float time;

        public final int type;

        public SampleInfo(float time, int type) {
            this.time = time;
            this.type = type;
        }
    }

    /**
     * A sample of all the streams at a given moment in time.
     * @Deprecated
     * TODO: Remove all usages then remove this class.
     */
    public static class Sample {

        /**
         * The time of the sample. In seconds since the start of the sampling.
         */
        public final float time;

        public final float[] values;

        public final int type;

        public Sample(float time, int type, @NonNull float[] values) {
            this.time = time;
            this.values = values;
            this.type = type;
        }
    }
}
