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

package com.android.tools.chartlib.visual;

import com.android.tools.chartlib.Animatable;
import com.android.tools.chartlib.AnimatedComponent;
import com.android.tools.chartlib.Choreographer;

import java.util.List;

import javax.swing.JPanel;

/**
 * Represent a Visual Test, containing a group of UI components, including charts.
 * Classes inheriting {@code VisualTest} must implement the abstract methods according to
 * documentation in order to work properly.
 */
public abstract class VisualTest {

    /**
     * Main panel of the VisualTest, which contains all the other elements.
     */
    private JPanel mPanel;

    private static final int CHOREOGRAPHER_FPS = 60;

    private Choreographer mChoreographer;

    /**
     * Thread to be used to update components data.
     * If set, it is going to be interrupted in {@code reset}. Note that if the subclass creates
     * some other threads, it should be responsible for keeping track and interrupting them when
     * necessary.
     */
    protected Thread mUpdateDataThread;

    public void initialize() {
        mChoreographer = new Choreographer(CHOREOGRAPHER_FPS);
        mChoreographer.register(createComponentsList());
        mPanel = create();
        mChoreographer.setParentContainer(mPanel);
    }

    public JPanel getPanel() {
        return mPanel;
    }

    public final Choreographer getChoreographer() {
        return mChoreographer;
    }

    /**
     * The {@code Animatable} components should be created in this method.
     * @return {@code List} containing the components created.
     */
    protected abstract List<Animatable> createComponentsList();

    /**
     * A {@code JPanel} containing the UI elements should be created and returned here.
     * It can use elements created in {@code createComponentsList}.
     */
    protected abstract JPanel create();

    /**
     * The {@code AnimatedComponent} should be added to {@code components}.
     */
    protected abstract void registerComponents(List<AnimatedComponent> components);

    /**
     * Interrupt active threads, clear all the components of the test and initialize it again.
     */
    protected void reset() {
        if (mUpdateDataThread != null) {
            mUpdateDataThread.interrupt();
        }
        initialize();
    }

    public abstract String getName();
}
