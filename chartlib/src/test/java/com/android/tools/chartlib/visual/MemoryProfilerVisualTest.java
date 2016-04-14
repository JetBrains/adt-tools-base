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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

public class MemoryProfilerVisualTest extends VisualTest {

    private static final String MEMORY_PROFILER_NAME = "Memory Profiler";

    public MemoryProfilerVisualTest() {
        // TODO: implement constructor
    }

    @Override
    protected void registerComponents(List<AnimatedComponent> components) {
        // TODO: register components
    }

    @Override
    public String getName() {
        return MEMORY_PROFILER_NAME;
    }

    @Override
    protected List<Animatable> createComponentsList() {
        // TODO: create components list
        return new ArrayList<>();
    }

    @Override
    protected JPanel create() {
        // TODO: implement memory profiler panel
        return new JPanel();
    }
}