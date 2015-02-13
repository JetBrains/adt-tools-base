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

import com.android.builder.profile.ExecutionType
import com.android.builder.profile.Recorder
import com.android.builder.profile.ThreadRecorder
import org.gradle.api.Project

/**
 * Groovy language helper to record execution spans.
 */
class GroovyRecorder {

    static <T> T record(Project project,ExecutionType executionType, Closure<T> closure) {
        // have to explicitly cast as groovy does not support inner classes with generics...
       return (T) ThreadRecorder.get().record(executionType, new Recorder.Block() {

            @Override
            Object call() throws Exception {
                return closure.call()
            }
        }, new Recorder.Property("project", project.name))
    }
}
