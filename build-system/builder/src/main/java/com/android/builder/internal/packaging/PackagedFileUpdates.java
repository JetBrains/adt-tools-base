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

package com.android.builder.internal.packaging;

import com.android.annotations.NonNull;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.res2.FileStatus;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Utilities to handle {@link PackagedFileUpdate} objects.
 */
final class PackagedFileUpdates {

    /**
     * Creates a set of updates based on a map of update. This set will
     * contain one entry per entry in the set in a 1-1 match.
     *
     * @param set the set with updates
     * @return the transform set
     */
    @NonNull
    static Set<PackagedFileUpdate> fromIncrementalRelativeFileSet(
            @NonNull Map<RelativeFile, FileStatus> set) {
        Set<PackagedFileUpdate> r = Sets.newHashSet();
        for (Map.Entry<RelativeFile, FileStatus> entry : set.entrySet()) {
            r.add(new PackagedFileUpdate(entry.getKey(),
                    entry.getKey().getOsIndependentRelativePath(), entry.getValue()));
        }

        return r;
    }
}
