/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.perflib.vmtrace.viz;

import com.android.annotations.NonNull;
import com.android.tools.perflib.vmtrace.Call;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.VmTraceData;

import java.awt.Color;

public class RenderContext {
    private final VmTraceData mTraceData;
    private ClockType mRenderClock;
    private boolean mUseInclusiveTimeForColorAssignment;

    public RenderContext(VmTraceData traceData, ClockType renderClock) {
        mTraceData = traceData;
        mRenderClock = renderClock;
    }

    public void setRenderClock(@NonNull ClockType type) {
        mRenderClock = type;
    }

    public void setUseInclusiveTimeForColorAssignment(boolean en) {
        mUseInclusiveTimeForColorAssignment = en;
    }

    @NonNull
    public ClockType getRenderClock() {
        return mRenderClock;
    }

    // Sequential color palette that works across both light and dark backgrounds
    private static final Color[] QUANTIZED_COLORS = {
            new Color(226, 230, 189),
            new Color(235, 228, 139),
            new Color(242, 221, 128),
            new Color(246, 210, 119),
            new Color(246, 197, 111),
            new Color(242, 180, 104),
            new Color(234, 161, 98),
            new Color(223, 139, 91),
            new Color(207, 115, 85),
            new Color(188, 88, 77),
            new Color(166, 57, 69),
            new Color(142, 6, 59),
    };

    /** Index into {@link #QUANTIZED_COLORS} where the crossover from light to dark happens. */
    private static final int BRIGHT_TO_DARK_CROSSOVER_INDEX = 9;

    private int getColorIndex(double percent) {
        int i = (int) (percent * QUANTIZED_COLORS.length / 100);
        return i >= QUANTIZED_COLORS.length ? QUANTIZED_COLORS.length - 1 : i;
    }

    /**
     * Returns the fill color for a particular call. The fill color is dependent on its
     * inclusive thread percentage time.
     */
    @NonNull
    public Color getFillColor(Call c, String threadName) {
        double percent = mTraceData.getDurationPercentage(c, threadName, mRenderClock,
                mUseInclusiveTimeForColorAssignment);
        return QUANTIZED_COLORS[getColorIndex(percent)];
    }

    /**
     * Returns the font color for a particular call. This returns a color complementary to
     * {@link #getFillColor}, so that text rendered on top of that color is distinguishable
     * from the background.
     */
    @NonNull
    public Color getFontColor(Call c, String threadName) {
        double percent = mTraceData.getDurationPercentage(c, threadName, mRenderClock,
                mUseInclusiveTimeForColorAssignment);
        return getColorIndex(percent) < BRIGHT_TO_DARK_CROSSOVER_INDEX ? Color.BLACK : Color.WHITE;
    }
}
