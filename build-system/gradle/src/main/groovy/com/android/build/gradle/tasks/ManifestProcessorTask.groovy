/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.tasks
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.google.common.base.Function
import com.google.common.base.Joiner
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import org.gradle.api.tasks.OutputFile
/**
 * A task that processes the manifest
 */
public abstract class ManifestProcessorTask extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    /**
     * The processed Manifest.
     */
    @OutputFile
    File manifestOutputFile

    /**
     * Serialize a map key+value pairs into a comma separated list. Map elements are sorted to
     * ensure stability between instances.
     * @param mapToSerialize the map to serialize.
     */
    protected String serializeMap(Map<String, String> mapToSerialize) {
        Joiner keyValueJoiner = Joiner.on(":");
        // transform the map on a list of key:value items, sort it and concatenate it.
        return Joiner.on(",").join(
                Lists.newArrayList(Iterables.transform(
                        mapToSerialize.entrySet(),
                        new Function<Map.Entry<String, String>, String>() {

                            @Override
                            public String apply(final Map.Entry<String, String> input) {
                                return keyValueJoiner.join(input.getKey(), input.getValue());
                            }
                        })).sort())
    }
}
