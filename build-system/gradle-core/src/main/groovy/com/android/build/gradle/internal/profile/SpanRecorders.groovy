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

package com.android.build.gradle.internal.profile

import com.android.annotations.NonNull
import com.android.builder.profile.ExecutionType
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import org.gradle.api.Project

/**
 * Groovy language helper to record execution spans.
 */
class SpanRecorders {

    public final static String PROJECT = "project";
    public final static String VARIANT = "variant";

    static <T> T record(@NonNull ExecutionType executionType, @NonNull Closure<T> closure) {
        // have to explicitly cast as groovy does not support inner classes with generics...
        return (T) ThreadRecorder.get().record(executionType, new Recorder.Block() {

            @Override
            Object call() throws Exception {
                return closure.call()
            }
        })
    }

    static <T> T record(@NonNull Project project,
            @NonNull ExecutionType executionType,
            @NonNull Closure<T> closure) {
        // have to explicitly cast as groovy does not support inner classes with generics...
       return (T) ThreadRecorder.get().record(executionType, new Recorder.Block() {

            @Override
            Object call() throws Exception {
                return closure.call()
            }
        }, new Recorder.Property(PROJECT, project.getName()))
    }

    /**
     * Records an execution span, using a Java {@link Recorder.Block}
     */
    static <T> T record(@NonNull Project project,
            @NonNull ExecutionType executionType,
            @NonNull Recorder.Block<T> block,
            Recorder.Property... properties) {
        List<Recorder.Property> mergedProperties = new ArrayList<>(properties.length + 1);
        mergedProperties.addAll(properties);
        mergedProperties.add(new Recorder.Property(PROJECT, project.getName()))
        return (T) ThreadRecorder.get().record(
                executionType, block,
                mergedProperties.toArray(new Recorder.Property[mergedProperties.size()]))
    }
}
