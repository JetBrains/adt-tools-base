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

import com.android.build.gradle.ndk.NdkExtension
import com.android.builder.core.BuilderConstants
import com.android.builder.model.AndroidProject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.nativebinaries.internal.ProjectSharedLibraryBinary
import org.gradle.nativebinaries.language.c.tasks.CCompile
import org.gradle.nativebinaries.language.cpp.tasks.CppCompile

/**
 * Configure settings used by the native binaries.
 */
class NdkConfigurationAction implements Action<Project> {

    NdkExtension ndkExtension

    NdkBuilder ndkBuilder

    NdkConfigurationAction(NdkBuilder ndkBuilder, NdkExtension ndkExtension) {
        this.ndkExtension = ndkExtension
        this.ndkBuilder = ndkBuilder
    }

    public void execute(Project project) {
        project.model {
            buildTypes {
                maybeCreate(BuilderConstants.DEBUG)
                maybeCreate(BuilderConstants.RELEASE)
            }
        }
        project.libraries.create(ndkExtension.getModuleName())
        configureProperties(project)
    }

    void configureProperties(Project project) {

        project.sources.getByName(ndkExtension.getModuleName()) {
            c {
                source {
                    setSrcDirs(
                            ndkExtension.getSourceSets().getByName(BuilderConstants.MAIN)
                                    .getSrcDirs())
                    include "**/*.c"
                }
            }
            cpp {
                source {
                    setSrcDirs(
                            ndkExtension.getSourceSets().getByName(BuilderConstants.MAIN)
                                    .getSrcDirs())
                    include "**/*.cpp"
                    include "**/*.cc"
                }
            }
        }

        project.libraries.getByName(ndkExtension.getModuleName()) {
            binaries.withType(ProjectSharedLibraryBinary.class) {
                    ProjectSharedLibraryBinary binary ->
                cCompiler.define "ANDROID"
                cppCompiler.define "ANDROID"
                cCompiler.define "ANDROID_NDK"
                cppCompiler.define "ANDROID_NDK"

                // Set output library filename.
                sharedLibraryFile = new File(
                        ndkBuilder.getOutputDirectory( buildType, targetPlatform),
                        "/lib" + ndkExtension.getModuleName() + ".so")

                // Replace output directory of compile tasks.
                binary.tasks.withType(CCompile) {
                    String sourceSetName = objectFileDir.name
                    objectFileDir = project.file(
                            "$project.buildDir/$AndroidProject.FD_INTERMEDIATES/objectFiles/" +
                                    "${binary.namingScheme.outputDirectoryBase}/$sourceSetName")
                }
                binary.tasks.withType(CppCompile) {
                    String sourceSetName = objectFileDir.name
                    objectFileDir = project.file(
                            "$project.buildDir/$AndroidProject.FD_INTERMEDIATES/objectFiles/" +
                                    "${binary.namingScheme.outputDirectoryBase}/$sourceSetName")
                }

                String sysroot = ndkBuilder.getSysroot(targetPlatform)
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

                // Currently do not support customization of stl library.
                cppCompiler.args "-I${ndkBuilder.getNdkDirectory()}/sources/cxx-stl/stlport/stlport"
                cppCompiler.args "-I${ndkBuilder.getNdkDirectory()}/sources/cxx-stl//gabi++/include"

                NativeToolSpecificationFactory.create(ndkBuilder, buildType, targetPlatform).apply(binary)

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
    }
}
