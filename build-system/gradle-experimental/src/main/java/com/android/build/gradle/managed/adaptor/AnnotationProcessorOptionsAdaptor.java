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

package com.android.build.gradle.managed.adaptor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreAnnotationProcessorOptions;
import com.android.build.gradle.managed.AnnotationProcessorOptions;
import com.android.build.gradle.managed.KeyValuePair;

import org.gradle.api.Named;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An adaptor to convert a managed.AnnotationProcessorOptions to a CoreAnnotationProcessorOptions.
 */
public class AnnotationProcessorOptionsAdaptor implements CoreAnnotationProcessorOptions{

    @NonNull
    private final AnnotationProcessorOptions annotationProcessorOptions;

    public AnnotationProcessorOptionsAdaptor(
            @NonNull AnnotationProcessorOptions annotationProcessorOptions) {
        this.annotationProcessorOptions = annotationProcessorOptions;
    }

    @Override
    @NonNull
    public List<String> getClassNames() {
        return annotationProcessorOptions.getClassNames();
    }

    @Nullable
    public Boolean getIncludeCompileClasspath() {
        return annotationProcessorOptions.getIncludeClasspath();
    }

    @Override
    @NonNull
    public Map<String, String> getArguments() {
        return annotationProcessorOptions.getArguments().values().stream()
                .collect(Collectors.toMap(
                        KeyValuePair::getName,
                        KeyValuePair::getValue));
    }
}
