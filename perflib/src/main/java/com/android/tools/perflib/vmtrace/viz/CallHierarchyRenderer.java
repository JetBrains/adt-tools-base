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
import com.android.tools.perflib.vmtrace.MethodInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.utils.HtmlBuilder;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/** Renders the call hierarchy rooted at a given call that is part of the trace. */
public class CallHierarchyRenderer {
    /** Height in pixels for a single call instance. Its length is proportional to its duration. */
    private static final int PER_LEVEL_HEIGHT_PX = 10;
    private static final int PADDING = 1;

    private static final int TEXT_HEIGHT = 6;
    private static final int TEXT_LEFT_PADDING = 5;

    private final VmTraceData mTraceData;
    private final Call mTopCall;
    private final int mYOffset;

    private final Rectangle mLayout = new Rectangle();
    private final Point2D.Float mSrc = new Point2D.Float();
    private final Point2D.Float mDst = new Point2D.Float();

    private Font myFont;

    public CallHierarchyRenderer(@NonNull VmTraceData vmTraceData, @NonNull Call c, int yOffset) {
        mTraceData = vmTraceData;
        mTopCall = c;
        mYOffset = yOffset;
    }

    /**
     * Renders the call hierarchy on a given graphics context.
     * This essentially iterates through every single call in the hierarchy and renders it if it is
     * visible in the current viewport.
     */
    public void render(Graphics2D g) {
        Rectangle clip = g.getClipBounds();

        Iterator<Call> it = mTopCall.getCallHierarchyIterator();
        while (it.hasNext()) {
            Call c = it.next();

            fillLayoutBounds(c, mLayout);

            // no need to render if it is is not in the current viewport.
            if (!clip.intersects(mLayout)) {
                continue;
            }

            // no need to render if it is too small (arbitrarily assumed to be < 1 px wide)
            double widthOnScreen = g.getTransform().getScaleX() * mLayout.width;
            if (widthOnScreen < 1) {
                continue;
            }

            // obtain the fill color based on its importance
            Color fillColor = getFillColor(c);
            g.setColor(fillColor);
            g.fillRect(mLayout.x, mLayout.y, mLayout.width, mLayout.height);

            // paint its name within the rectangle if possible
            String name = getName(c);
            drawString(g, name, mLayout, getFontColor(c));
        }
    }

    private void drawString(Graphics2D g, String name, Rectangle bounds, Color fontColor) {
        if (myFont == null) {
            myFont = g.getFont().deriveFont(8.0f);
        }
        g.setFont(myFont);
        g.setColor(fontColor);

        AffineTransform origTx = g.getTransform();

        mSrc.x = bounds.x + TEXT_LEFT_PADDING;
        mSrc.y = bounds.y + TEXT_HEIGHT;

        double availableWidth = g.getTransform().getScaleX() * bounds.width;

        // When drawing a string, we want its location to be transformed by the current viewport
        // transform, but not the text itself (we don't want it zoomed out or in).
        origTx.transform(mSrc, mDst);
        g.setTransform(new AffineTransform());

        double stringWidth = g.getFontMetrics().stringWidth(name);
        if (availableWidth > stringWidth) {
            g.drawString(name, mDst.x, mDst.y);
        }

        g.setTransform(origTx);
    }

    /** Fills the layout bounds corresponding to a given call in the given Rectangle object. */
    private void fillLayoutBounds(Call c, Rectangle layoutBounds) {
        layoutBounds.x = (int) (c.getEntryGlobalTime() - mTopCall.getEntryGlobalTime() + PADDING);
        layoutBounds.y = c.getDepth() * PER_LEVEL_HEIGHT_PX + mYOffset + PADDING;
        layoutBounds.width  = (int) c.getInclusiveGlobalTime() - 2 * PADDING;
        layoutBounds.height = PER_LEVEL_HEIGHT_PX - 2 * PADDING;
    }

    /** Get the tooltip corresponding to given location (in item coordinates). */
    public String getToolTipFor(int x, int y) {
        Iterator<Call> it = mTopCall.getCallHierarchyIterator();
        while (it.hasNext()) {
            Call c = it.next();

            fillLayoutBounds(c, mLayout);
            if (mLayout.contains(x, y)) {
                return formatToolTip(c);
            }
        }
        return null;
    }

    private static final DecimalFormat PERCENTAGE_FORMATTER = new DecimalFormat("#.##");

    private String formatToolTip(Call c) {
        HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.openHtmlBody();

        htmlBuilder.addHeading(getName(c), "black");

        long span = c.getExitGlobalTime() - c.getEntryGlobalTime();
        TimeUnit unit = mTraceData.getTimeUnits();
        String entryGlobal = TimeUtils.makeHumanReadable(c.getEntryGlobalTime(), span, unit);
        String entryThread = TimeUtils.makeHumanReadable(c.getEntryThreadTime(), span, unit);
        String exitGlobal = TimeUtils.makeHumanReadable(c.getExitGlobalTime(), span, unit);
        String exitThread = TimeUtils.makeHumanReadable(c.getExitThreadTime(), span, unit);
        String durationGlobal = TimeUtils.makeHumanReadable(
                c.getExitGlobalTime() - c.getEntryGlobalTime(), span, unit);
        String durationThread = TimeUtils.makeHumanReadable(
                c.getExitThreadTime() - c.getEntryThreadTime(), span, unit);

        htmlBuilder.beginTable();
        htmlBuilder.addTableRow(true, "", "Wall Time", "Thread Time");
        htmlBuilder.addTableRow("Entry", entryGlobal, entryThread);
        htmlBuilder.addTableRow("Exit", exitGlobal, exitThread);
        htmlBuilder.addTableRow("Duration", durationGlobal, durationThread);
        htmlBuilder.endTable();

        MethodInfo info = getMethodInfo(c);
        htmlBuilder.add("Inclusive Time (across all invocations): ");
        htmlBuilder.beginBold();
        htmlBuilder.add(PERCENTAGE_FORMATTER.format(info.getInclusiveThreadPercent()));
        htmlBuilder.add("%");
        htmlBuilder.endBold();

        htmlBuilder.closeHtmlBody();
        return htmlBuilder.getHtml();
    }

    @NonNull
    private String getName(@NonNull Call c) {
        return getMethodInfo(c).getShortName();
    }

    private MethodInfo getMethodInfo(@NonNull Call c) {
        long methodId = c.getMethodId();
        return mTraceData.getMethod(methodId);
    }

    /**
     * Returns the fill color for a particular call. The fill color is dependent on its
     * inclusive thread percentage time.
     */
    private Color getFillColor(Call c) {
        MethodInfo info = mTraceData.getMethod(c.getMethodId());
        int percent = quantize(info.getInclusiveThreadPercent());
        return getFill(percent);
    }

    /**
     * Returns the font color for a particular call. This returns a color complementary to
     * {@link #getFillColor(com.android.tools.perflib.vmtrace.Call)}, so that text rendered
     * on top of that color is distinguishable from the background.
     */
    private Color getFontColor(Call c) {
        MethodInfo info = mTraceData.getMethod(c.getMethodId());
        int percent = quantize(info.getInclusiveThreadPercent());
        return getFontColor(percent);
    }

    private static final Color[] QUANTIZED_COLORS = {
            new Color(247,251,255),
            new Color(222,235,247),
            new Color(198,219,239),
            new Color(158,202,225),
            new Color(107,174,214),
            new Color(66,146,198),
            new Color(33,113,181),
            new Color(8,81,156),
            new Color(8,48,107),
    };

    private Color getFill(int percent) {
        int i = percent * QUANTIZED_COLORS.length / 100;
        if (i >= QUANTIZED_COLORS.length) {
            i = QUANTIZED_COLORS.length - 1;
        }
        return QUANTIZED_COLORS[i];
    }

    private Color getFontColor(int percent) {
        int i = percent / 10;
        if (i >= QUANTIZED_COLORS.length) {
            i = QUANTIZED_COLORS.length - 1;
        }

        return  i > 6 ? Color.WHITE : Color.BLACK;
    }

    private int quantize(float inclusiveThreadPercent) {
        return ((int)(inclusiveThreadPercent + 9) / 10) * 10;
    }
}
