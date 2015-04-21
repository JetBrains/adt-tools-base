/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;

public class AnimatedComponent extends JComponent implements ActionListener, HierarchyListener {

    protected static final Font DEFAULT_FONT = new Font("Sans", Font.PLAIN, 10);

    protected final Timer mTimer;

    /**
     * The length of the last frame in seconds.
     */
    protected float mFrameLength;

    protected long mLastRenderTime;

    protected boolean mDrawDebugInfo;

    protected boolean mUpdateData;

    protected boolean mStep;

    private List<String> mDebugInfo;

    public AnimatedComponent(int fps) {
        mUpdateData = true;
        mTimer = new Timer(1000 / fps, this);
        mDebugInfo = new LinkedList<String>();
        addHierarchyListener(this);
    }

    /**
     * A linear interpolation that accumulates over time. This gives an exponential effect where the
     * value {@code from} moves towards the value {@code to} at a rate of {@code fraction} per
     * second. The actual interpolated amount depends on the current frame length.
     *
     * @param from     the value to interpolate from.
     * @param to       the target value.
     * @param fraction the interpolation fraction.
     * @return the interpolated value.
     */
    protected float lerp(float from, float to, float fraction) {
        float q = (float) Math.pow(1.0f - fraction, mFrameLength);
        return from * q + to * (1.0f - q);
    }

    public boolean isDrawDebugInfo() {
        return mDrawDebugInfo;
    }

    public void setDrawDebugInfo(boolean drawDebugInfo) {
        mDrawDebugInfo = drawDebugInfo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        // Update frame length.
        long now = System.nanoTime();
        mFrameLength = (now - mLastRenderTime) / 1000000000.0f;
        mLastRenderTime = now;

        if (mUpdateData || mStep) {
            mStep = false;
            updateData();
        }

        draw(g2d);

        if (mDrawDebugInfo) {
            doDebugDraw(g2d);
        }
        g2d.dispose();
    }

    protected void addDebugInfo(String format, Object... values) {
        if (mDrawDebugInfo) {
            mDebugInfo.add(String.format(format, values));
        }
    }

    private void doDebugDraw(Graphics2D g) {
        debugDraw(g);

        addDebugInfo("Render time: %.2fms", (System.nanoTime() - mLastRenderTime) / 1000000.f);
        addDebugInfo("FPS: %.2f", (1.0f / mFrameLength));
        g.setFont(DEFAULT_FONT);
        g.setColor(Color.BLACK);
        int i = 0;
        for (String s : mDebugInfo) {
            g.drawString(s, getSize().width - 150, getSize().height - 10 * i++ - 5);
        }
        mDebugInfo.clear();
    }

    /**
     * First step of the animation, this is where the data is read and the current animation
     * values are fixed.
     */
    protected void updateData() {
    }

    /**
     * Renders the data constructed in the update phase to the given graphics context.
     */
    protected void draw(Graphics2D g) {
    }

    /**
     * Draws visual debug information.
     */
    protected void debugDraw(Graphics2D g) {
    }


    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        repaint();
    }

    @Override
    public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
        if (mTimer.isRunning() && !isShowing()) {
            mTimer.stop();
        } else if (!mTimer.isRunning() && isShowing()) {
            mTimer.start();
        }
    }

    public void setUpdateData(boolean updateData) {
        mUpdateData = updateData;
    }

    public void step() {
        mStep = true;
    }
}
