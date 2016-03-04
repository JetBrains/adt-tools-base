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

import java.util.Map;
import java.util.Set;

import gnu.trove.list.array.TLongArrayList;

/*
 * Custom View to draw a bar per smap entry visualizing its shared/private memory usage
 */
public class MemorySmapsView extends View {

    // TODO  allow changing DATA_PER_ENTRY
    private static int DATA_PER_ENTRY = 2;
    private static int PAGE_SIZE_IN_KB = 4;
    private static int COLUMN_WIDTH = 8;

    private int mWidth;
    private int mHeight;
    private int mDataCount;
    private long mMax = Long.MIN_VALUE;

    private TLongArrayList mData = new TLongArrayList();
    private Paint[] mPaint = null;

    public MemorySmapsView(Context c, AttributeSet attrs) {
        super(c, attrs);

        Paint privatePaint = new Paint();
        privatePaint.setColor(Color.RED);
        privatePaint.setAntiAlias(false);
        privatePaint.setStyle(Paint.Style.STROKE);
        privatePaint.setStrokeWidth(COLUMN_WIDTH);

        Paint sharedPaint = new Paint();
        sharedPaint.setColor(Color.BLUE);
        sharedPaint.setAntiAlias(false);
        sharedPaint.setStyle(Paint.Style.STROKE);
        sharedPaint.setStrokeWidth(COLUMN_WIDTH);

        mPaint = new Paint[]{sharedPaint, privatePaint};
    }

    public void clearData() {
        mData.clear();
        mDataCount = 0;
        mMax = Long.MIN_VALUE;
        invalidate();
    }

    public void addData(boolean clearData, Set<Map.Entry<String, long[]>> entries) {
        if (clearData) {
            clearData();
        }

        for (Map.Entry<String, long[]> entry : entries) {
            // long array structure - shared/private/pss/rss/virtualspace
            if (entry.getValue()[2] == 0) {
                continue;   // skip zero pss entries
            }

            long sharedData = entry.getValue()[0];
            long privateData = entry.getValue()[1];

            mData.add(sharedData);
            mData.add(privateData);

            mMax = Math.max(mMax, sharedData + privateData);
            mDataCount++;
        }

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mWidth = w;
        mHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int i = 0; i < mDataCount; i++) {
            // TODO auto adjust column width based on data size
            if (i >= mWidth / COLUMN_WIDTH) {
                break;
            }

            float scaledSharedHeight = (float) mData.get(i * DATA_PER_ENTRY) * mHeight / (float) mMax;
            float scaledPrivateHeight = (float) mData.get(i * DATA_PER_ENTRY + 1) * mHeight / (float) mMax;

            canvas.drawLine(i * COLUMN_WIDTH, mHeight, i * COLUMN_WIDTH, mHeight - scaledSharedHeight, mPaint[i % 2]);
            canvas.drawLine(i * COLUMN_WIDTH, mHeight - scaledSharedHeight, i * COLUMN_WIDTH, mHeight - scaledSharedHeight - scaledPrivateHeight, mPaint[i % 2]);
        }
    }
}
