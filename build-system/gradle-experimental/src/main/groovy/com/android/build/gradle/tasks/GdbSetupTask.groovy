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

import com.android.build.gradle.internal.tasks.DefaultAndroidTask
import com.android.build.gradle.ndk.NdkExtension
import com.android.build.gradle.ndk.internal.NdkHandler
import com.android.build.gradle.ndk.internal.StlConfiguration
import com.google.common.base.Charsets
import com.google.common.collect.Sets
import com.google.common.io.Files
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.language.c.CSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.nativeplatform.NativeBinarySpec

/**
 * Task to create gdb.setup for native code debugging.
 */
class GdbSetupTask extends DefaultAndroidTask {
    @Input
    NdkHandler ndkHandler

    @Input
    NdkExtension extension

    @Input
    NativeBinarySpec binary

    @Input
    File outputDir

    @TaskAction
    void taskAction() {
        File gdbSetupFile = new File(outputDir, "gdb.setup")

        StringBuilder sb = new StringBuilder()

        sb.append("set solib-search-path ${outputDir.toString()}\n")
        sb.append("directory ")
        sb.append("${ndkHandler.getSysroot(binary.targetPlatform)}/usr/include ")

        Set<String> sources = Sets.newHashSet();
        binary.getSource().withType(CSourceSet) { sourceSet ->
            sources.addAll(sourceSet.source.srcDirs*.toString())
        }
        binary.getSource().withType(CppSourceSet) { sourceSet ->
            sources.addAll(sourceSet.source.srcDirs*.toString())
        }
        sources.addAll(StlConfiguration.getStlSources(ndkHandler, extension.stl))
        sb.append(sources.join(" "))

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        Files.write(sb.toString(), gdbSetupFile, Charsets.UTF_8)
    }
}
