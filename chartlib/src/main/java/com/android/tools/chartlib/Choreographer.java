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

package com.android.tools.chartlib;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.Timer;

public class Choreographer implements ActionListener {

    private static final float NANOSECONDS_IN_SECOND = 1000000000.0f;
    private static final float DEFAULT_FRAME_LENGTH = 1.0f / 60.0f;

    private final List<Animatable> mComponents;
    private final Timer mTimer;
    private boolean mUpdate;
    private long mFrameTime;

    private JComponent mParentContainer;

    public Choreographer(int fps) {
        mComponents = new LinkedList<Animatable>();
        mUpdate = true;
        mTimer = new Timer(1000 / fps, this);
        mTimer.start();
    }

    /**
     * Sets the parent container to trigger a single repaint on all its children components
     * at the end of every cycle.
     * TODO - We should refactor this. Instead of having to call setParentContainer,
     * we can either make it part of the constructor, or use a listener pattern (e.g. postAnimated)
     * to trigger the master panel's repaint externally.
     */
    public void setParentContainer(JComponent parent) {
        mParentContainer = parent;
    }

    public void register(Animatable animatable) {
        mComponents.add(animatable);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        long now = System.nanoTime();
        float frame = (now - mFrameTime) / NANOSECONDS_IN_SECOND;
        mFrameTime = now;

        if (!mUpdate) {
            return;
        }
        step(frame);
    }

  /**
   * Legacy method to animate components. Each component animates on its own and has a choreographer
   * that is bound to that component's visibility.
   */
  public static void animate(final AnimatedComponent component) {
      final Choreographer choreographer = new Choreographer(30);
      choreographer.register(component);
      HierarchyListener listener = new HierarchyListener() {
          @Override
          public void hierarchyChanged(HierarchyEvent ignored) {
              if (choreographer.mTimer.isRunning() && !component.isShowing()) {
                  choreographer.mTimer.stop();
              } else if (!choreographer.mTimer.isRunning() && component.isShowing()) {
                  choreographer.mTimer.start();
              }
          }
      };
      listener.hierarchyChanged(null);
      component.addHierarchyListener(listener);
  }

    public void setUpdate(boolean update) {
        mUpdate = update;
    }

    public void step() {
        step(DEFAULT_FRAME_LENGTH);
    }

    private void step(float frameLength) {
        for (Animatable component : mComponents) {
            component.animate(frameLength);
        }

        for (Animatable component : mComponents) {
            component.postAnimate();
        }

        if (mParentContainer != null) {
            // If a parent container is assigned, calling repaint on it
            // would force repaint on all its descendants in the correct z-order.
            // This prevent the children components' repaint call to trigger
            // redundant redraws on overlapping elements.
            mParentContainer.repaint();
        }
    }

    /**
     * A linear interpolation that accumulates over time. This gives an exponential effect where the
     * value {@code from} moves towards the value {@code to} at a rate of {@code fraction} per
     * second. The actual interpolated amount depends on the current frame length.
     *
     * @param from          the value to interpolate from.
     * @param to            the target value.
     * @param fraction      the interpolation fraction.
     * @param frameLength   the frame length in seconds.
     * @return the interpolated value.
     */
    public static float lerp(float from, float to, float fraction, float frameLength) {
        float q = (float) Math.pow(1.0f - fraction, frameLength);
        return from * q + to * (1.0f - q);
    }

    public static double lerp(double from, double to, float fraction, float frameLength) {
        double q = Math.pow(1.0f - fraction, frameLength);
        return from * q + to * (1.0 - q);
    }
}
