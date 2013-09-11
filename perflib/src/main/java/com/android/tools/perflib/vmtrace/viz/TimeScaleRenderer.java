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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.concurrent.TimeUnit;

public class TimeScaleRenderer {
    /** Offset of the horizontal line indicating the timeline from the top of the screen. */
    private static final int TIMELINE_Y_OFFSET = 20;

    /** Horizontal padding from a marker to where its corresponding time is drawn. */
    public static final int TIMELINE_UNIT_HORIZONTAL_PADDING = 5;

    /** Vertical padding from the horizontal timeline to where the time units are drawn. */
    public static final int TIMELINE_UNIT_VERTICAL_PADDING = 5;

    private final long mStartTime;
    private final TimeUnit mTimeUnits;

    private double[] mPoints = new double[8];

    private AffineTransform mViewTransform;
    private AffineTransform mViewTransformInverse;

    public TimeScaleRenderer(long startTime, TimeUnit unit) {
        mStartTime = startTime;
        mTimeUnits = unit;
    }

    public void paint(Graphics2D g2d, AffineTransform viewPortTransform, int screenWidth) {
        AffineTransform originalTransform = g2d.getTransform();

        // draw the custom timeline for the current viewport transformation
        drawTimeLine(g2d, viewPortTransform, screenWidth);

        g2d.setTransform(originalTransform);
    }

    private void drawTimeLine(Graphics2D g2d, AffineTransform viewPortTransform, int screenWidth) {
        createInverse(viewPortTransform);

        // (0,y)
        mPoints[0] = 0;
        mPoints[1] = 0; // Note: We don't care about the y-coordinate here.

        // (screenWidth, y)
        mPoints[2] = screenWidth;
        mPoints[3] = 0; // Note: We don't care about the y-coordinate here.

        // The timeline goes from (0,y) to (screenWidth,y) in screen space. We apply the
        // inverse of the view transform to convert these to item space.
        mViewTransformInverse.transform(mPoints, 0, mPoints, 4, 2);

        // Offset both the left and right end points by the start time.
        long start = (long) mPoints[4] + mStartTime;
        long end = (long) mPoints[6] + mStartTime;

        g2d.setColor(Color.BLACK);

        // draw the horizontal timeline
        g2d.drawLine(0, TIMELINE_Y_OFFSET, screenWidth, TIMELINE_Y_OFFSET);

        // draw the time at the leftmost end of the screen corresponding to (0,y)
        String time = TimeUtils.makeHumanReadable(start, end - start, mTimeUnits);
        g2d.drawString(time,
                0 + TIMELINE_UNIT_HORIZONTAL_PADDING,
                TIMELINE_Y_OFFSET - TIMELINE_UNIT_VERTICAL_PADDING);

        // draw the time at the leftmost end of the screen corresponding to (screen width,y)
        time = TimeUtils.makeHumanReadable(end, end - start, mTimeUnits);
        g2d.drawString(time,
                screenWidth - g2d.getFontMetrics().stringWidth(time)
                        - TIMELINE_UNIT_HORIZONTAL_PADDING,
                TIMELINE_Y_OFFSET - TIMELINE_UNIT_VERTICAL_PADDING);
    }

    public int getLayoutHeight() {
        return TIMELINE_Y_OFFSET + 10;
    }

    private void createInverse(AffineTransform viewPortTransform) {
        if (!viewPortTransform.equals(mViewTransform)) {
            // cache source transformation matrix
            mViewTransform = new AffineTransform(viewPortTransform);

            try {
                mViewTransformInverse = mViewTransform.createInverse();
            } catch (NoninvertibleTransformException e) {
                // This scenario should never occur since the viewport is only zoomed or panned,
                // both of which are invertible.
                mViewTransformInverse = new AffineTransform();
            }
        }
    }
}
