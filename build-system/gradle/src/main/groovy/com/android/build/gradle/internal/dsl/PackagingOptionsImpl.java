/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.builder.model.PackagingOptions;
import com.google.common.collect.Sets;
import org.gradle.api.tasks.Input;

import java.util.Collections;
import java.util.Set;

public class PackagingOptionsImpl implements PackagingOptions {

    private Set<String> excludes;

    @Override
    @NonNull
    @Input
    public Set<String> getExcludes() {
        if (excludes == null) {
            return Collections.emptySet();
        }
        return excludes;
    }

    public void exclude(String path) {
        if (excludes == null) {
            excludes = Sets.newHashSet();
        }

        excludes.add(path);
    }
}
