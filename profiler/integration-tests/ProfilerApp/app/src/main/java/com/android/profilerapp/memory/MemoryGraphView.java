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

package com.android.profilerapp.memory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/*
 * Custom view to graph GraphDataEntry as they are being added.
 * This currently uses a circular buffer so old data is discarded
 * as the buffer is filled so the graph "slides" as data streams in.
 */
public class MemoryGraphView extends View {

    private static int DATA_WIDTH = 2;

    private int mNextUpdateIndex;
    private int mDataCount;
    private int mMaxCount;
    private int mMax;
    private int mWidth;
    private int mHeight;

    private GraphDataEntry[] mData;
    private Paint[] mPaint;
    private Paint mLinePaint;

    public MemoryGraphView(Context c, AttributeSet attrs) {
        super (c, attrs);

        mLinePaint = new Paint();
        mLinePaint.setColor(Color.BLACK);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(DATA_WIDTH);

        Paint usedPaint = new Paint();
        usedPaint.setColor(Color.RED);
        usedPaint.setStyle(Paint.Style.STROKE);
        usedPaint.setStrokeWidth(DATA_WIDTH);

        Paint currentPaint = new Paint();
        currentPaint.setColor(Color.GREEN);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeWidth(DATA_WIDTH);

        mPaint = new Paint[] {usedPaint, currentPaint};

        clearData();
    }

    public void clearData() {
        mData = null;
        mMax = mNextUpdateIndex = mDataCount = 0;
        invalidate();
    }

    /*
     * TODO support more than two data points per entry
     */
    public void addPoint(int data1, int data2, boolean hasEvent) {
        if (mData == null) {
            mData = new GraphDataEntry[mMaxCount];
        }

        GraphDataEntry entry = new GraphDataEntry();
        entry.data1 = data1;
        entry.data2 = data2;
        entry.hasEvent = hasEvent;

        mData[mNextUpdateIndex] = entry;

        if (Math.max(data1, data2) > mMax) {
            mMax = Math.max(data1, data2);
        }

        // circular buffer
        mNextUpdateIndex = (mNextUpdateIndex + 1) % mMaxCount;
        mDataCount = Math.min(mMaxCount, mDataCount + 1);

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        clearData();
        mWidth = w;
        mHeight = h;
        mMaxCount = mWidth / DATA_WIDTH;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDataCount > 0) {
            for (int i = 0; i < mDataCount; i++) {
                int circulateIndex = (mNextUpdateIndex + (mMaxCount - mDataCount) + i) % mMaxCount;
                GraphDataEntry entry = mData[circulateIndex];

                // draw data point 2
                float scaledHeight2 = mHeight - (float) entry.data2 * mHeight / (float) mMax;
                canvas.drawLine(i * 2, mHeight, i * 2, scaledHeight2, mPaint[1]);

                // draw data point 1
                float scaledHeight1 = mHeight - (float) entry.data1 * mHeight / (float) mMax;
                canvas.drawLine(i * 2, mHeight, i * 2, scaledHeight1, mPaint[0]);

                // draw event (GC) line
                if (entry.hasEvent) {
                    canvas.drawLine(i * 2, mHeight, i * 2, 0, mLinePaint);
                }
            }
        }
    }

    private class GraphDataEntry {
        public int data1;
        public int data2;
        public boolean hasEvent;
    }
}
