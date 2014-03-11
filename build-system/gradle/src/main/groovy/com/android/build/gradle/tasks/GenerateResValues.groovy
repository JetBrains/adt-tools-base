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
package com.android.build.gradle.tasks
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.builder.compiling.ResValueGenerator
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public class GenerateResValues extends BaseTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File resOutputDir

    // ----- PRIVATE TASK API -----

    @Input @Nested
    List<Object> items

    @TaskAction
    void generate() {
        File folder = getResOutputDir()
        List<Object> resolvedItems = getItems()

        if (resolvedItems.isEmpty()) {
            folder.deleteDir()
        } else {
            ResValueGenerator generator = new ResValueGenerator(folder)
            generator.addItems(getItems())

            generator.generate()
        }
    }
}
