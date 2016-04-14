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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;

import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.ConventionTask;

import java.util.concurrent.Callable;

import groovy.lang.GroovyObject;

/**
 * Helper class to dynamically access conventionMapping of a task.
 */
public final class ConventionMappingHelper {
    private ConventionMappingHelper() {}

    public static void map(@NonNull Task task, @NonNull String key, @NonNull Callable<?> value) {
        if (task instanceof ConventionTask) {
            ((ConventionTask) task).getConventionMapping().map(key, value);
        } else if (task instanceof GroovyObject) {
            ConventionMapping conventionMapping =
                    (ConventionMapping) ((GroovyObject) task).getProperty("conventionMapping");
            conventionMapping.map(key, value);
        } else {
            throw new IllegalArgumentException(
                    "Don't know how to apply convention mapping to task of type " + task.getClass().getName());
        }
    }
}
