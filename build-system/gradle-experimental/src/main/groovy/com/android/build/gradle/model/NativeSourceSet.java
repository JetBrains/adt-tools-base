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

package com.android.build.gradle.model;

import com.android.build.gradle.internal.AbstractNativeDependentSourceSet;

import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;

/**
 * Implementation of LanguageSourceSet for native sources.
 */
public class NativeSourceSet extends AbstractNativeDependentSourceSet implements LanguageSourceSet,
        HeaderExportingSourceSet {

    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet implicitHeaders;

    public NativeSourceSet() {
        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", fileResolver);
        this.implicitHeaders = new DefaultSourceDirectorySet("implicit headers", fileResolver);
    }

    @Override
    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    @Override
    public void exportedHeaders(Action<? super SourceDirectorySet> config) {
        config.execute(getExportedHeaders());
    }

    @Override
    public SourceDirectorySet getImplicitHeaders() {
        return implicitHeaders;
    }
}
