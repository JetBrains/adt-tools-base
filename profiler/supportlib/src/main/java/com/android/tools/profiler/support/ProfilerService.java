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
package com.android.tools.profiler.support;

import com.android.tools.profiler.support.profilers.MemoryProfiler;
import com.android.tools.profiler.support.profilers.NetworkProfiler;
import com.android.tools.profiler.support.profilers.ProfilerComponent;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class is used via instrumentation
public class ProfilerService {

    public static final String STUDIO_PROFILER = "StudioProfiler";

    private static ProfilerService sInstance;
    private final List<ProfilerComponent> mComponents;

    /**
     * Initialization method called multiple times from many entry points in the application.
     * Not thread safe so, when instrumented, it needs to be added in the main thread.
     */
    public static void initialize() {
        if (sInstance != null) {
            return;
        }
        sInstance = new ProfilerService();
        Log.i(STUDIO_PROFILER, "Initializing advanced profiling.");
    }

    public ProfilerService() {
        mComponents = new ArrayList<ProfilerComponent>();
        mComponents.add(new NetworkProfiler());
        mComponents.add(new MemoryProfiler());
    }
}
