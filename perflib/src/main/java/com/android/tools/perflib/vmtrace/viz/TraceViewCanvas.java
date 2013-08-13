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
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.utils.SparseArray;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

/**
 * A canvas that displays the call hierarchy for a single thread.
 * The trace and the the thread to be displayed are specified using
 * {@link #setTrace(com.android.tools.perflib.vmtrace.VmTraceData, String)} and
 * {@link #displayThread(String)} methods.
 */
public class TraceViewCanvas extends JComponent {
    private static final Color BACKGROUND_COLOR = Color.LIGHT_GRAY;

    /**
     * Interactor that listens to mouse events, interprets them as zoom/pan events, and provides
     * the resultant viewport transform.
     * */
    private final ZoomPanInteractor mZoomPanInteractor;

    /** The viewport transform takes into account the current zoom and translation/pan values. */
    private AffineTransform mViewPortTransform;

    /** The screen transform accounts for the location of the canvas within the JFrame. */
    private AffineTransform mScreenTransform;

    /** Combined {@link #mViewPortTransform} * {@link #mScreenTransform}. */
    private AffineTransform mTransform;

    private VmTraceData mTraceData;

    private TimeScaleRenderer mTimeScaleRenderer;
    private CallHierarchyRenderer mCallHierarchyRenderer;

    public TraceViewCanvas() {
        mScreenTransform = new AffineTransform();
        mViewPortTransform = new AffineTransform();
        mTransform = new AffineTransform();

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
    }

    public void setTrace(@NonNull VmTraceData traceData, @NonNull String threadName) {
        mTraceData = traceData;
        displayThread(threadName);
    }

    public void displayThread(@NonNull String threadName) {
        mCallHierarchyRenderer = null;
        mTimeScaleRenderer = null;

        int threadId = findThreadIdFromName(threadName);
        if (threadId < 0) {
            return;
        }
        Call topLevelCall = mTraceData.getTopLevelCall(threadId);
        if (topLevelCall == null) {
            return;
        }

        long start = topLevelCall.getEntryGlobalTime();
        long end = topLevelCall.getExitGlobalTime();

        mTimeScaleRenderer = new TimeScaleRenderer(start, mTraceData.getTimeUnits());
        int yOffset = mTimeScaleRenderer.getLayoutHeight();
        mCallHierarchyRenderer = new CallHierarchyRenderer(mTraceData, topLevelCall, yOffset);

        // Scale so that the full trace occupies 90% of the screen width.
        double width = getWidth();
        double sx = (width - width/10.0f) / (end - start);

        // Initialize display so that the full trace is visible and takes up most of the view.
        mZoomPanInteractor.setToScaleX(sx, 1); // make everything visible
        mZoomPanInteractor.translateBy(50, 0); // shift over the start of the trace
        updateViewPortTransform(mZoomPanInteractor.getTransform());
    }

    private int findThreadIdFromName(String threadName) {
        SparseArray<String> threads = mTraceData.getThreads();
        for (int i = 0; i < threads.size(); i++) {
            if (threads.valueAt(i).equalsIgnoreCase(threadName)) {
                return threads.keyAt(i);
            }
        }

        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1000, 800);
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

        // set the viewport * screen space transform
        g2d.setTransform(mTransform);

        // paint stack layout view
        if (mCallHierarchyRenderer != null) {
            mCallHierarchyRenderer.render(g2d);
        }

        // paint timeline at top
        if (mTimeScaleRenderer != null) {
            mTimeScaleRenderer.paint(g2d, mScreenTransform, mViewPortTransform, getWidth());
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
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        mScreenTransform.setToTranslation(x, y);
        updateTransform();
    }

    private void updateViewPortTransform(AffineTransform tx) {
        mViewPortTransform = new AffineTransform(tx);
        updateTransform();
    }

    private void updateTransform() {
        mTransform = new AffineTransform(mScreenTransform);
        mTransform.concatenate(mViewPortTransform);

        repaint();
    }
}
