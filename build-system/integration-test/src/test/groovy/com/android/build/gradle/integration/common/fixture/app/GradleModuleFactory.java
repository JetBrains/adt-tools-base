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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Factory to create GradleModule.
 *
 * The GradleModule *must* implement a constructor with parameters
 * (File, String, List&lt;? extends GradleModule&gt;).
 */
public class GradleModuleFactory {
    Class<? extends GradleModule> moduleClass;

    public <T extends GradleModule> GradleModuleFactory(Class<T> moduleClass) {
        this.moduleClass = moduleClass;
    }

    GradleModule createModule(
            @NonNull File location,
            @NonNull String path,
            @NonNull List<? extends GradleModule> projectDeps) {
        GradleModule module;
        try {
            module = moduleClass.getDeclaredConstructor(
                    File.class,
                    String.class,
                    List.class).newInstance(location, path, projectDeps);
        } catch (Exception e) {
            throw new RuntimeException("Unable to find constructor for GradleModule: " + e.toString());
        }
        return module;
    }
}
