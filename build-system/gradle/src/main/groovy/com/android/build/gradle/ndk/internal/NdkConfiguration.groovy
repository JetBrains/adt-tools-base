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

package com.android.build.gradle.ndk.internal

import com.android.build.gradle.model.AndroidComponentModelSourceSet
import com.android.build.gradle.ndk.NdkExtension
import com.android.build.gradle.tasks.GdbSetupTask
import com.android.builder.core.BuilderConstants
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.c.CSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.language.c.tasks.CCompile
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.platform.base.BinarySpec

/**
 * Configure settings used by the native binaries.
 */
class NdkConfiguration {
    public static void configureProperties(
            NativeLibrarySpec library,
            AndroidComponentModelSourceSet sources,
            File buildDir,
            NdkExtension ndkExtension,
            NdkHandler ndkHandler) {
        Collection<String> abiList = ndkHandler.getSupportedAbis()
        abiList.each {
            library.targetPlatform(it)
        }

        library.binaries.withType(SharedLibraryBinarySpec) { binary ->
            sourceIfExist(binary, sources, "main")
            sourceIfExist(binary, sources, binary.flavor.name)
            sourceIfExist(binary, sources, binary.buildType.name)
            sourceIfExist(binary, sources, binary.flavor.name + binary.buildType.name.capitalize())

            cCompiler.define "ANDROID"
            cppCompiler.define "ANDROID"
            cCompiler.define "ANDROID_NDK"
            cppCompiler.define "ANDROID_NDK"

            // Set output library filename.
            binary.sharedLibraryFile =
                    new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary) + "/" +
                            NdkNamingScheme.getSharedLibraryFileName(ndkExtension.getModuleName()))

            // Replace output directory of compile tasks.
            binary.tasks.withType(CCompile) { task ->
                String sourceSetName = task.objectFileDir.name
                task.objectFileDir = NdkNamingScheme.getObjectFilesOutputDirectory(
                        binary, buildDir, sourceSetName)
            }
            binary.tasks.withType(CppCompile) { task ->
                String sourceSetName = task.objectFileDir.name
                task.objectFileDir = NdkNamingScheme.getObjectFilesOutputDirectory(
                                binary, buildDir, sourceSetName)
            }

            String sysroot = ndkHandler.getSysroot(binary.targetPlatform)
            cCompiler.args  "--sysroot=$sysroot"
            cppCompiler.args  "--sysroot=$sysroot"
            linker.args "--sysroot=$sysroot"

            if (ndkExtension.getRenderscriptNdkMode()) {
                cCompiler.args "-I$sysroot/usr/include/rs"
                cCompiler.args "-I$sysroot/usr/include/rs/cpp"
                cppCompiler.args "-I$sysroot/usr/include/rs"
                cppCompiler.args "-I$sysroot/usr/include/rs/cpp"
                linker.args "-L$sysroot/usr/lib/rs"
            }

            NativeToolSpecificationFactory.create(ndkHandler, binary.buildType, binary.targetPlatform).apply(binary)

            // Add flags defined in NdkExtension
            if (ndkExtension.getcFlags() != null) {
                cCompiler.args ndkExtension.getcFlags()
            }
            if (ndkExtension.getCppFlags() != null) {
                cppCompiler.args ndkExtension.getCppFlags()
            }
            for (String ldLibs : ndkExtension.getLdLibs()) {
                linker.args "-l$ldLibs"
            }
        }
    }

    public static void createTasks(
            TaskContainer tasks,
            SharedLibraryBinarySpec binary,
            File buildDir,
            NdkExtension ndkExtension,
            NdkHandler ndkHandler) {
        StlConfiguration.apply(ndkHandler, ndkExtension.getStl(), tasks, buildDir, binary)

        if (binary.buildType.name.equals(BuilderConstants.DEBUG)) {
            setupNdkGdbDebug(tasks, binary, buildDir, ndkExtension, ndkHandler)
        }
    }

    /**
     * Add the sourceSet with the specified name to the binary if such sourceSet is defined.
     */
    private static void sourceIfExist(
            BinarySpec binary,
            AndroidComponentModelSourceSet projectSourceSet,
            String sourceSetName) {
        FunctionalSourceSet sourceSet = projectSourceSet.findByName(sourceSetName)
        if (sourceSet != null) {
            binary.source(sourceSet)
        }
    }

    /**
     * Setup tasks to create gdb.setup and copy gdbserver for NDK debugging.
     */
    private static void setupNdkGdbDebug(
            TaskContainer tasks,
            NativeBinarySpec binary,
            File buildDir,
            NdkExtension ndkExtension,
            NdkHandler handler) {
        Task copyGdbServerTask = tasks.create(
                name: NdkNamingScheme.getTaskName(binary, "copy", "GdbServer"),
                type: Copy) {
            from(new File(
                    handler.getPrebuiltDirectory(binary.targetPlatform),
                    "gdbserver/gdbserver"))
            into(new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary)))
        }
        binary.builtBy copyGdbServerTask

        Task createGdbSetupTask = tasks.create(
                name: NdkNamingScheme.getTaskName(binary, "create", "Gdbsetup"),
                type: GdbSetupTask) { def task ->
            task.ndkHandler = handler
            task.extension = ndkExtension
            task.binary = binary
            task.outputDir = new File(buildDir, NdkNamingScheme.getOutputDirectoryName(binary))
        }
        binary.builtBy createGdbSetupTask
    }
}
