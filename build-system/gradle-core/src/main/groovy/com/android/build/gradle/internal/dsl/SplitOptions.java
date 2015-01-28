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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base data representing how an APK should be split for a given dimension (density, abi).
 */
public class SplitOptions {

    private boolean enable = false;
    private boolean reset = false;
    private Set<String> exclude;
    private Set<String> include;

    /**
     * Whether to split in this dimension.
     */
    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * Collection of exclude patterns.
     */
    public Set<String> getExclude() {
        return exclude;
    }

    /**
     * Collection of include patterns.
     */
    public Set<String> getInclude() {
        return include;
    }

    /**
     * Collection of exclude patterns.
     */
    public void setExclude(@NonNull List<String> list) {
        exclude = Sets.newHashSet(list);
    }

    public void exclude(@NonNull String... excludes) {
        if (exclude == null) {
            exclude = Sets.newHashSet(excludes);
            return;
        }

        exclude.addAll(Arrays.asList(excludes));
    }

    public void setInclude(@NonNull List<String> list) {
        include = Sets.newHashSet(list);
    }

    public void include(@NonNull String... includes) {
        if (include == null) {
            include = Sets.newHashSet(includes);
            return;
        }

        include.addAll(Arrays.asList(includes));
    }

    /**
     * Resets the list of included split configuration.
     *
     * Use this before calling include, in order to manually configure the list of configuration
     * to split on, rather than excluding from the default list.
     */
    public void reset() {
        reset = true;
    }

    /**
     * Returns a list of all applicable filters for this dimension.
     *
     * The list can return null, indicating that the no-filter option must also be used.
     *
     * @param allFilters the available filters, excluding the no-filter option.
     *
     * @return the filters to use.
     */
    @NonNull
    public Set<String> getApplicableFilters(@NonNull Set<String> allFilters) {
        if (!enable) {
            return Collections.singleton(null);
        }

        Set<String> results = reset ?
                Sets.<String>newHashSetWithExpectedSize(allFilters.size() + 1) :
                Sets.newHashSet(allFilters);

        if (exclude != null) {
            results.removeAll(exclude);
        }

        if (include != null) {
            // we need to make sure we only include stuff that's from the full list.
            for (String inc : include) {
                if (allFilters.contains(inc)) {
                    results.add(inc);
                }
            }
        }

        return results;
    }
}
