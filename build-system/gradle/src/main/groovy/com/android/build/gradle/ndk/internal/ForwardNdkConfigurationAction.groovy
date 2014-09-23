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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.ndk.NdkExtension
import com.android.build.gradle.ndk.NdkPlugin
import com.android.builder.model.BuildType
import org.gradle.api.Action
import org.gradle.api.Project

/**
 * Configure NDK extension base on configurations of the Android plugin if it is applied.
 */
public class ForwardNdkConfigurationAction implements Action<Project> {

    public void execute(Project project) {
        BasePlugin androidPlugin = project.getPlugins().findPlugin(AppPlugin.class);
        if (androidPlugin == null) {
            androidPlugin = project.getPlugins().findPlugin(LibraryPlugin.class);
        }
        if (androidPlugin == null) {
            return;
        }

        NdkPlugin ndkPlugin = project.getPlugins().getPlugin(NdkPlugin.class)
        NdkExtension ndkExtension = ndkPlugin.getNdkExtension();
        BaseExtension androidExtension = androidPlugin.getExtension();
        if (ndkExtension.getCompileSdkVersion() == null) {
            // Retrieve compileSdkVersion from Android plugin if it is not set for the NDK plugin.
            ndkExtension.setCompileSdkVersion(androidExtension.getCompileSdkVersion());
        }

        // Set build types and product flavors.
        androidExtension.getBuildTypes().all { BuildType buildType ->
            project.model {
                buildTypes {
                    maybeCreate(buildType.name)
                }
            }
        }

        if (!androidExtension.getProductFlavors().isEmpty()) {
            androidPlugin.getVariantDataList().each { BaseVariantData data ->
                project.model {
                    flavors {
                        maybeCreate(data.getVariantConfiguration().getFlavorName())
                    }
                }
            }
        }

        // Create source sets.
        androidExtension.sourceSets.all { AndroidSourceSet androidSourceSet ->
            AndroidSourceDirectorySet ndkSourceSet =
                    ndkExtension.sourceSets.maybeCreate(androidSourceSet.name)
            AndroidSourceDirectorySet jni = androidSourceSet.getJni()
            ndkSourceSet.srcDirs jni.getSrcDirs()
            ndkSourceSet.include jni.getIncludes()
            ndkSourceSet.exclude jni.getExcludes()
        }
    }
}
