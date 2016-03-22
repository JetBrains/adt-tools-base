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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.Managed;

/**
 * DSL object for javaCompileOptions.
 */
public class JavaCompileOptions implements CoreJavaCompileOptions {
    @NonNull
    private AnnotationProcessorOptions annotationProcessorOptions;

    @VisibleForTesting
    public JavaCompileOptions() {
        annotationProcessorOptions = new AnnotationProcessorOptions();
    }

    public JavaCompileOptions(Instantiator instantiator) {
        annotationProcessorOptions = instantiator.newInstance(AnnotationProcessorOptions.class);
    }

    /**
     * Options for configuration the annotation processor.
     */
    @Override
    @NonNull
    public AnnotationProcessorOptions getAnnotationProcessorOptions() {
        return annotationProcessorOptions;
    }

    public void annotationProcessorOptions(Action<AnnotationProcessorOptions> configAction) {
        configAction.execute(annotationProcessorOptions);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("annotationProcessorOptions", annotationProcessorOptions)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaCompileOptions that = (JavaCompileOptions) o;
        return Objects.equal(annotationProcessorOptions, that.annotationProcessorOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annotationProcessorOptions);
    }
}