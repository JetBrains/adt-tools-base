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
import java.awt.geom.Path2D;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;

/**
 * Base class for components that should change their look over time.
 *
 * At a minimum, child classes should override {@link #updateData()} and {@link #draw(Graphics2D)},
 * as well as pay attention to the field {@link #mFrameLength} as it controls the behavior of timed
 * animations.
 */
public abstract class AnimatedComponent extends JComponent implements Animatable {

    protected static final Font DEFAULT_FONT = new Font("Sans", Font.PLAIN, 10);

    /**
     * The cached length of the last frame in seconds.
     */
    protected float mFrameLength;

    protected long mLastRenderTime;

    protected boolean mDrawDebugInfo;

    private List<String> mDebugInfo;

    public AnimatedComponent() {
        mDebugInfo = new LinkedList<String>();
    }

    public final boolean isDrawDebugInfo() {
        return mDrawDebugInfo;
    }

    public final void setDrawDebugInfo(boolean drawDebugInfo) {
        mDrawDebugInfo = drawDebugInfo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        draw(g2d);
        if (mDrawDebugInfo) {
            doDebugDraw(g2d);
        }
        g2d.dispose();
    }

    protected final void addDebugInfo(String format, Object... values) {
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
     * First step of the animation, this is where the data is read and the current animation values
     * are fixed.
     */
    protected abstract void updateData();

    /**
     * Renders the data constructed in the update phase to the given graphics context.
     */
    protected abstract void draw(Graphics2D g);

    /**
     * Draws visual debug information.
     */
    protected void debugDraw(Graphics2D g) {
    }

    protected static void drawArrow(Graphics2D g, float x, float y, float dx, float dy, float len,
            Color color) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x, y);
        path.lineTo(x + dx * len, y + dy * len);
        path.lineTo(x + dx * (len - 10) + dy * 10, y + dy * (len - 10) - dx * 10);
        path.lineTo(x + dx * (len - 10) - dy * 10, y + dy * (len - 10) + dx * 10);
        g.setColor(color);
        g.draw(path);
    }

    protected static void drawMarker(Graphics2D g, float x, float y, Color color) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x - 10, y);
        path.lineTo(x + 10, y);
        path.moveTo(x, y - 10);
        path.lineTo(x, y + 10);
        g.setColor(color);
        g.draw(path);
    }

    @Override
    public void animate(float frameLength) {
        mFrameLength = frameLength;
        this.updateData();
        this.repaint();
    }
}
