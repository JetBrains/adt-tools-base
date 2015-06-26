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
import com.android.tools.perflib.vmtrace.MethodInfo;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.utils.HtmlBuilder;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static com.android.tools.perflib.vmtrace.ClockType.THREAD;
import static com.android.tools.perflib.vmtrace.ClockType.GLOBAL;

/** Renders the call hierarchy rooted at a given call that is part of the trace. */
public class CallHierarchyRenderer {
    /** Height in pixels for a single call instance. Its length is proportional to its duration. */
    private static final int PER_LEVEL_HEIGHT_PX = 10;
    private static final int PADDING = 1;

    private static final int TEXT_HEIGHT = 6;
    private static final int TEXT_LEFT_PADDING = 5;

    private final VmTraceData mTraceData;
    private final ThreadInfo mThread;
    private final Call mTopCall;
    private final int mYOffset;
    private final TimeUnit mLayoutTimeUnits;
    private final RenderContext mRenderContext;

    private final Rectangle2D mLayout = new Rectangle2D.Double();
    private final Point2D mTmpPoint1 = new Point2D.Double();
    private final Point2D mTmpPoint2 = new Point2D.Double();

    private Font mFont;

    public CallHierarchyRenderer(@NonNull VmTraceData vmTraceData, @NonNull ThreadInfo thread,
            int yOffset, TimeUnit defaultTimeUnits, RenderContext renderContext) {
        mTraceData = vmTraceData;
        mThread = thread;
        mTopCall = thread.getTopLevelCall();
        mYOffset = yOffset;
        mLayoutTimeUnits = defaultTimeUnits;
        mRenderContext = renderContext;
    }

    /**
     * Renders the call hierarchy on a given graphics context.
     * This essentially iterates through every single call in the hierarchy and renders it if it is
     * visible in the current viewport.
     */
    public void render(Graphics2D g, AffineTransform viewPortTransform) {
        Rectangle clip = g.getClipBounds();

        Iterator<Call> it = mTopCall.getCallHierarchyIterator();
        while (it.hasNext()) {
            Call c = it.next();

            // obtain layout in item space
            fillLayoutBounds(c, mLayout);

            // transform based on the current viewport (scale + translate)
            transformRect(viewPortTransform, mLayout);

            // no need to render if it is is not in the current viewport.
            if (!clip.intersects(mLayout)) {
                continue;
            }

            // no need to render if it is too small (arbitrarily assumed to be < 1 px wide)
            if (mLayout.getWidth() < 1) {
                continue;
            }

            // obtain the fill color based on its importance
            Color fillColor = mRenderContext.getFillColor(c, mThread);
            g.setColor(fillColor);
            g.fill(mLayout);

            // paint its name within the rectangle if possible
            String name = getName(c);
            drawString(g, name, mLayout, mRenderContext.getFontColor(c, mThread));
        }
    }

    private Rectangle2D transformRect(AffineTransform viewPortTransform, Rectangle2D rect) {
        mTmpPoint1.setLocation(rect.getX(), rect.getY());
        mTmpPoint2.setLocation(rect.getWidth(), rect.getHeight());

        viewPortTransform.transform(mTmpPoint1, mTmpPoint1);
        viewPortTransform.deltaTransform(mTmpPoint2, mTmpPoint2);

        rect.setRect(mTmpPoint1.getX(),
                mTmpPoint1.getY(),
                mTmpPoint2.getX(),
                mTmpPoint2.getY());
        return rect;
    }

    private void drawString(Graphics2D g, String name, Rectangle2D bounds, Color fontColor) {
        if (mFont == null) {
            mFont = g.getFont().deriveFont(8.0f);
        }
        g.setFont(mFont);
        g.setColor(fontColor);

        AffineTransform origTx = g.getTransform();

        mTmpPoint1.setLocation(bounds.getX() + TEXT_LEFT_PADDING, bounds.getY() + TEXT_HEIGHT);

        double availableWidth = g.getTransform().getScaleX() * bounds.getWidth();

        // When drawing a string, we want its location to be transformed by the current viewport
        // transform, but not the text itself (we don't want it zoomed out or in).
        origTx.transform(mTmpPoint1, mTmpPoint1);
        g.setTransform(new AffineTransform());

        double stringWidth = g.getFontMetrics().stringWidth(name);
        if (availableWidth > stringWidth) {
            g.drawString(name, (float) mTmpPoint1.getX(), (float) mTmpPoint1.getY());
        }

        g.setTransform(origTx);
    }

    /** Fills the layout bounds corresponding to a given call in the given Rectangle object. */
    private void fillLayoutBounds(Call c, Rectangle2D layoutBounds) {
        ClockType renderClock = mRenderContext.getRenderClock();
        double x = c.getEntryTime(renderClock, mLayoutTimeUnits)
                - mTopCall.getEntryTime(renderClock, mLayoutTimeUnits)
                + PADDING;
        double y = c.getDepth() * PER_LEVEL_HEIGHT_PX + mYOffset + PADDING;
        double width  = c.getInclusiveTime(renderClock, mLayoutTimeUnits) - 2 * PADDING;
        double height = PER_LEVEL_HEIGHT_PX - 2 * PADDING;
        layoutBounds.setRect(x, y, width, height);
    }

    /** Get the tooltip corresponding to given location (in item coordinates). */
    public String getToolTipFor(double x, double y) {
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

        htmlBuilder.addHeading(getMethodInfo(c).getFullName(), "black");

        long span = c.getExitTime(GLOBAL, TimeUnit.NANOSECONDS) -
                c.getEntryTime(GLOBAL, TimeUnit.NANOSECONDS);
        TimeUnit unit = TimeUnit.NANOSECONDS;
        String entryGlobal = TimeUtils.makeHumanReadable(c.getEntryTime(GLOBAL, unit), span, unit);
        String entryThread = TimeUtils.makeHumanReadable(c.getEntryTime(THREAD, unit), span, unit);
        String exitGlobal = TimeUtils.makeHumanReadable(c.getExitTime(GLOBAL, unit), span, unit);
        String exitThread = TimeUtils.makeHumanReadable(c.getExitTime(THREAD, unit), span, unit);
        String durationGlobal = TimeUtils.makeHumanReadable(
                c.getExitTime(GLOBAL, unit) - c.getEntryTime(GLOBAL, unit), span, unit);
        String durationThread = TimeUtils.makeHumanReadable(
                c.getExitTime(THREAD, unit) - c.getEntryTime(THREAD, unit), span, unit);

        htmlBuilder.beginTable();
        htmlBuilder.addTableRow("Wallclock Time:", durationGlobal,
                String.format("(from %s to %s)", entryGlobal, exitGlobal));
        htmlBuilder.addTableRow("CPU Time:", durationThread,
                String.format("(from %s to %s)", entryThread, exitThread));
        htmlBuilder.endTable();

        htmlBuilder.newline();
        htmlBuilder.add("Inclusive Time: ");
        htmlBuilder.beginBold();
        double inclusivePercentage = mTraceData.getDurationPercentage(c, mThread,
                mRenderContext.getRenderClock(), true /* use inclusive time */);
        htmlBuilder.add(PERCENTAGE_FORMATTER.format(inclusivePercentage));
        htmlBuilder.add("%");
        htmlBuilder.endBold();

        htmlBuilder.newline();
        htmlBuilder.add("Exclusive Time: ");
        htmlBuilder.beginBold();
        double exclusivePercentage = mTraceData.getDurationPercentage(c, mThread,
                mRenderContext.getRenderClock(), false /* don't use inclusive time */);
        htmlBuilder.add(PERCENTAGE_FORMATTER.format(exclusivePercentage));
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
}
