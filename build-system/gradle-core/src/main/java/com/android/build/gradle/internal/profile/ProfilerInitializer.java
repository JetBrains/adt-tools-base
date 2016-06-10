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

package com.android.build.gradle.internal.profile;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.profile.ProcessRecorderFactory;

import org.gradle.api.Project;

/**
 * Initialize the {@link ProcessRecorderFactory} using a given project.
 *
 * <p>Is separate from {@code ProcessRecorderFactory} as {@code ProcessRecorderFactory} does not
 * depend on gradle classes.
 */
public final class ProfilerInitializer {

    private ProfilerInitializer() {
        //Util class.
    }

    /**
     * Initialize the {@link ProcessRecorderFactory}. Idempotent.
     *
     * @param project the current Gradle {@link Project}.
     */
    public static void init(@NonNull Project project) {
        ProcessRecorderFactory.initialize(
                project.getGradle().getGradleVersion(),
                new LoggerWrapper(project.getLogger()),
                project.getRootProject().file("profiler" + System.currentTimeMillis() + ".json"));
    }
}
