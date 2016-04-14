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

package com.android.build.gradle.model.internal;

import com.android.build.gradle.internal.AbstractNativeDependentSourceSet;
import com.android.build.gradle.model.NativeSourceSet;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;

/**
 * Implementation of {@link NativeSourceSet}
 */
public class DefaultNativeSourceSet extends AbstractNativeDependentSourceSet
        implements NativeSourceSet {

    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet implicitHeaders;

    public DefaultNativeSourceSet() {
        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", fileResolver);
        this.implicitHeaders = new DefaultSourceDirectorySet("implicit headers", fileResolver);
    }

    @Override
    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    @Override
    public SourceDirectorySet getImplicitHeaders() {
        return implicitHeaders;
    }
}
