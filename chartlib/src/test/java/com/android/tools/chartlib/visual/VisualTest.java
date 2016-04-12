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

import com.android.tools.chartlib.AnimatedComponent;
import com.android.tools.chartlib.Choreographer;

import java.util.List;

import javax.swing.JPanel;

public abstract class VisualTest {

    /**
     * Main panel of the VisualTest, which contains all the other elements.
     */
    private JPanel mPanel;

    private static final int CHOREOGRAPHER_FPS = 60;

    protected Choreographer mChoreographer;

    protected VisualTest() {
        mChoreographer = new Choreographer(CHOREOGRAPHER_FPS);
    }

    abstract void registerComponents(List<AnimatedComponent> components);

    public abstract String getName();

    protected abstract JPanel create();

    public void initialize() {
        mPanel = create();
        mChoreographer.setParentContainer(mPanel);
    }

    public JPanel getPanel() {
        return mPanel;
    }

    public final Choreographer getChoreographer() {
        return mChoreographer;
    }
}
