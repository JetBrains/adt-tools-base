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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.LibraryCache
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public class PrepareLibraryTask extends DefaultTask {
    // on 1.2 we changed the content of the exploded-aar. We use a @input with a basic int
    // to increment whenever the exploded aar is moved around so that incremental builds still work

    /**
     * If the organization of the aar changed, we need to ensure the task is re-run when we
     * build with a different version of the plugin. Otherwise the paths will not match what the
     * plugin expects. (this happens in 1.2 where classes.jar is moved under jars/).
     * TODO: dynamically populate with the plugin version
     */
    @Input
    String pluginVersion = "1.2"

    @InputFile
    File bundle

    @OutputDirectory
    File explodedDir

    @TaskAction
    def prepare() {
        //LibraryCache.getCache().unzipLibrary(this.name, project, getBundle(), getExplodedDir())
        LibraryCache.unzipAar(getBundle(), getExplodedDir(), project)
    }
}
