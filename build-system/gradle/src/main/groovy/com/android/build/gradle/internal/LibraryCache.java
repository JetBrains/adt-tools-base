/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.LibraryDependencyImpl;
import com.android.build.gradle.internal.tasks.PrepareLibraryTask;
import com.google.common.collect.Maps;

import org.gradle.api.Project;
import org.gradle.util.GUtil;

import java.util.Map;

/**
 * Cache to library prepareTask.
 *
 * Each project creates its own version of LibraryDependencyImpl, but they all represent the
 * same library. This creates a single task that will unarchive the aar so that this is done only
 * once even for multi-module projects where 2+ modules depend on the same library.
 *
 * The prepareTask is created in the root project always.
 */
public class LibraryCache {

    @NonNull
    private static final LibraryCache sCache = new LibraryCache();

    @NonNull
    public static LibraryCache getCache() {
        return sCache;
    }

    /**
     * Map of LibraryDependencyImpl -> PrepareLibTask. The LibDependency task uses the
     * name of the library in the hashcode/equals methods.
     * The name is groupId:artifactName:version:classifier.
     */
    private final Map<LibraryDependencyImpl, PrepareLibraryTask> prepareTaskMap = Maps.newHashMap();

    /**
     * Handles the library and returns a task to "prepare" the library (ie unarchive it). The task
     * will be reused for all projects using the same library.
     *
     * @param project the project
     * @param library the library.
     * @return the prepare task.
     */
    public PrepareLibraryTask handleLibrary(@NonNull Project project,
            @NonNull LibraryDependencyImpl library) {
        String bundleName = GUtil
                .toCamelCase(library.getName().replaceAll("\\:", " "));

        PrepareLibraryTask prepareLibraryTask = prepareTaskMap.get(library);

        if (prepareLibraryTask == null) {
            prepareLibraryTask = project.getRootProject().getTasks().create(
                    "prepare" + bundleName + "Library", PrepareLibraryTask.class);

            prepareLibraryTask.setDescription("Prepare " + library.getName());
            prepareLibraryTask.bundle = library.getBundle();
            prepareLibraryTask.explodedDir = library.getBundleFolder();

            prepareTaskMap.put(library, prepareLibraryTask);
        }

        return prepareLibraryTask;
    }
}
