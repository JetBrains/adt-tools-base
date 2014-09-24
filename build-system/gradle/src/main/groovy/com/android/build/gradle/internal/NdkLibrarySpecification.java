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

package com.android.build.gradle.internal;

import com.android.build.gradle.model.NdkComponentModelPlugin;
import com.android.builder.core.VariantConfiguration;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.platform.base.Binary;

import java.io.File;
import java.util.Collection;

/**
 * A dependency on a NDK library within the build.
 */
public class NdkLibrarySpecification {
    private Project currentProject;

    private String targetProjectName;

    private NdkComponentModelPlugin getNdkPlugin() {
        Project targetProject = targetProjectName.isEmpty()
                    ? currentProject
                    : currentProject.getRootProject().project(targetProjectName);
        NdkComponentModelPlugin plugin = targetProject.getPlugins().findPlugin(NdkComponentModelPlugin.class);
        if (plugin == null) {
            throw new InvalidUserDataException(
                    "Unable to find android native plugin in project '" + targetProjectName + "'.");
        }
        return plugin;
    }

    public String getTargetProjectName() {
        return targetProjectName;
    }

    public NdkLibrarySpecification(Project currentProject, String targetProjectName) {
        this.currentProject = currentProject;
        this.targetProjectName = targetProjectName;
    }

    public Collection<? extends Binary> getBinaries(VariantConfiguration variantConfig) {
        return getNdkPlugin().getBinaries(variantConfig);
    }

    public Collection<File> getOutputDirectories(VariantConfiguration variantConfig) {
        return getNdkPlugin().getOutputDirectories(variantConfig);
    }
}
