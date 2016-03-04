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

import javax.swing.Timer;

public class Choreographer implements ActionListener {

    private static final float DEFAULT_FRAME_LENGTH = 1.0f / 60.0f;

    private final List<Animatable> mComponents;
    private final Timer mTimer;
    private boolean mUpdate;
    private long mFrameTime;

    public Choreographer(int fps) {
        mComponents = new LinkedList<Animatable>();
        mUpdate = true;
        mTimer = new Timer(1000 / fps, this);
        mTimer.start();
    }

    public void register(Animatable animatable) {
        mComponents.add(animatable);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        long now = System.nanoTime();
        float frame = (now - mFrameTime) / 1000000000.0f;
        mFrameTime = now;

        if (!mUpdate) {
            return;
        }
        step(frame);
    }

  /**
   * Legacy method to animate components. Each component animates on it's own and have a choreographer
   * that is bound tho that component's visibility.
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
    }
}
