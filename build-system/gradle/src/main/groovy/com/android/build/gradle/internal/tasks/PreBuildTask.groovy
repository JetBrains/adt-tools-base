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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 */
class PreBuildTask extends DefaultTask {

    BaseExtension extension

    @Input
    String getVersion() {
        return extension.buildToolsRevision.toShortString()
    }

    @TaskAction
    void preBuild() {
        // check on the build tools version.
        if (extension.buildToolsRevision.major < 19) {
            throw new RuntimeException("Build Tools Revision 19.0.0+ is required.")
        }
    }
}
