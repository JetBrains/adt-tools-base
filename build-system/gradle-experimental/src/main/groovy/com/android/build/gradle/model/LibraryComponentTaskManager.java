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

package com.android.build.gradle.model;

import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.DependencyManager;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantConfiguration;
import com.google.common.collect.ImmutableList;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.util.Collection;

/**
 * TaskManager for creating tasks in an Android library project with component model plugin.
 */
public class LibraryComponentTaskManager extends LibraryTaskManager {

    public LibraryComponentTaskManager(
            Project project,
            AndroidBuilder androidBuilder,
            AndroidConfig extension,
            SdkHandler sdkHandler,
            DependencyManager dependencyManager,
            ToolingModelBuilderRegistry toolingRegistry) {
        super(project, androidBuilder, extension, sdkHandler, dependencyManager, toolingRegistry);
        isNdkTaskNeeded = false;
    }

    @Override
    protected Collection<Object> getNdkBuildable(BaseVariantData variantData) {
        NdkComponentModelPlugin plugin = project.getPlugins().getPlugin(NdkComponentModelPlugin.class);
        return ImmutableList.<Object>copyOf(plugin.getBinaries(variantData.getVariantConfiguration()));
    }
    @Override
    public void configureScopeForNdk(VariantScope scope) {
        NdkComponentModelPlugin plugin = project.getPlugins().getPlugin(
                NdkComponentModelPlugin.class);
        scope.setNdkSoFolder(plugin.getOutputDirectories(scope.getVariantConfiguration()));

        VariantConfiguration config = scope.getVariantConfiguration();
        // TODO: NdkComponentModelPlugin should generate two .so files, one with debugging symbols
        // and one without.  For now, generate only one file with debugging symbol in the output
        // directory.
        for (Abi abi : NdkHandler.getAbiList()) {
            scope.addNdkDebuggableLibraryFolders(
                    abi,
                    new File(
                            scope.getGlobalScope().getBuildDir(),
                            NdkNamingScheme.getOutputDirectoryName(
                                    config.getBuildType().getName(),
                                    config.getFlavorName(),
                                    abi.getName())));
        }
    }
}
