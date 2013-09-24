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
import com.android.annotations.Nullable;
import com.android.tools.perflib.vmtrace.Call;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.MethodInfo;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * A canvas that displays the call hierarchy for a single thread. The trace and the the thread to be
 * displayed are specified using {@link #setTrace} and {@link #displayThread} methods.
 */
public class TraceViewCanvas extends JComponent {
    private static final Color BACKGROUND_COLOR =
            UIManager.getLookAndFeelDefaults().getColor("EditorPane.background");
    private static final int TOOLTIP_OFFSET = 10;

    /**
     * The time unit to use for all operations. Changing this changes the minimum resolution
     * that can be viewed on the canvas.
     */
    private static final TimeUnit DEFAULT_TIME_UNITS = TimeUnit.NANOSECONDS;

    /**
     * Interactor that listens to mouse events, interprets them as zoom/pan events, and provides the
     * resultant viewport transform.
     */
    private final ZoomPanInteractor mZoomPanInteractor;

    /** The viewport transform takes into account the current zoom and translation/pan values. */
    private AffineTransform mViewPortTransform;

    /** Inverse of {@link #mViewPortTransform}. */
    private AffineTransform mViewPortInverseTransform;

    private VmTraceData mTraceData;
    private Call mTopLevelCall;
    private RenderContext mRenderContext;

    private TimeScaleRenderer mTimeScaleRenderer;
    private CallHierarchyRenderer mCallHierarchyRenderer;

    private final Point2D mTmpPoint = new Point2D.Double();

    public TraceViewCanvas() {
        mViewPortTransform = new AffineTransform();
        mViewPortInverseTransform = new AffineTransform();

        mZoomPanInteractor = new ZoomPanInteractor();
        addMouseListener(mZoomPanInteractor);
        addMouseMotionListener(mZoomPanInteractor);
        addMouseWheelListener(mZoomPanInteractor);

        mZoomPanInteractor.addViewTransformListener(new ZoomPanInteractor.ViewTransformListener() {
            @Override
            public void transformChanged(@NonNull AffineTransform transform) {
                updateViewPortTransform(transform);
            }
        });

        addMouseMotionListener(ToolTipManager.sharedInstance());

        // Listen for the first hierarchy bounds change so as to get the initial width,
        // and then zoom fit once we know the width.
        addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                removeHierarchyBoundsListener(this);
                zoomFit();
            }
        });
    }

    public void setTrace(@NonNull VmTraceData traceData, @NonNull ThreadInfo thread,
            ClockType renderClock) {
        mTraceData = traceData;
        mRenderContext = new RenderContext(traceData, renderClock);
        displayThread(thread);
    }

    public void displayThread(@NonNull ThreadInfo thread) {
        mCallHierarchyRenderer = null;
        mTimeScaleRenderer = null;

        if (mTraceData == null) {
            return;
        }

        mTopLevelCall = thread.getTopLevelCall();
        if (mTopLevelCall == null) {
            return;
        }

        mTimeScaleRenderer = new TimeScaleRenderer(
                mTopLevelCall.getEntryTime(ClockType.GLOBAL, DEFAULT_TIME_UNITS),
                DEFAULT_TIME_UNITS);
        int yOffset = mTimeScaleRenderer.getLayoutHeight();
        mCallHierarchyRenderer = new CallHierarchyRenderer(mTraceData, thread, yOffset,
                DEFAULT_TIME_UNITS, mRenderContext);

        zoomFit();
    }

    public void setRenderClock(ClockType clock) {
        mRenderContext.setRenderClock(clock);
        repaint();
    }

    public void setUseInclusiveTimeForColorAssignment(boolean en) {
        mRenderContext.setUseInclusiveTimeForColorAssignment(en);
        repaint();
    }

    public void setHighlightMethods(@Nullable Set<MethodInfo> methods) {
        mRenderContext.setHighlightedMethods(methods);
        repaint();
    }

    public void zoomFit() {
        if (mTopLevelCall == null) {
            return;
        }

        long start = mTopLevelCall.getEntryTime(ClockType.GLOBAL, DEFAULT_TIME_UNITS);
        long end = mTopLevelCall.getExitTime(ClockType.GLOBAL, DEFAULT_TIME_UNITS);

        // Scale so that the full trace occupies 90% of the screen width.
        double width = getWidth();
        double sx = width * .9f / (end - start);

        // Guard against trying to zoom when the component doesn't know its width yet. Width is
        // usually 0 in such cases, but we just make it slightly general and check for width < 10.
        if (width < 10) {
            sx = Math.max(sx, 0.2);
        }

        // Initialize display so that the full trace is visible and takes up most of the view.
        mZoomPanInteractor.setToScaleX(sx, 1); // make everything visible
        mZoomPanInteractor.translateBy(50, 0); // shift over the start of the trace
        updateViewPortTransform(mZoomPanInteractor.getTransform());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        setRenderingHints(g2d);

        // fill with background color
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        if (mTraceData == null) {
            return;
        }

        // paint stack layout view
        if (mCallHierarchyRenderer != null) {
            mCallHierarchyRenderer.render(g2d, mViewPortTransform);
        }

        // paint timeline at top
        if (mTimeScaleRenderer != null) {
            mTimeScaleRenderer.paint(g2d, mViewPortTransform, getWidth());
        }
    }

    private void setRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (mTraceData == null || mCallHierarchyRenderer == null) {
            return null;
        }

        mTmpPoint.setLocation(event.getPoint());
        mViewPortInverseTransform.transform(mTmpPoint, mTmpPoint);
        return mCallHierarchyRenderer.getToolTipFor(mTmpPoint.getX(), mTmpPoint.getY());
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return new Point(event.getX() + TOOLTIP_OFFSET, event.getY() + TOOLTIP_OFFSET);
    }

    private void updateViewPortTransform(AffineTransform tx) {
        mViewPortTransform = new AffineTransform(tx);

        try {
            mViewPortInverseTransform = mViewPortTransform.createInverse();
        } catch (NoninvertibleTransformException e) {
            // This can't occur since we just do scale or pan, both of which are invertible
            mViewPortInverseTransform = new AffineTransform();
        }

        repaint();
    }
}
