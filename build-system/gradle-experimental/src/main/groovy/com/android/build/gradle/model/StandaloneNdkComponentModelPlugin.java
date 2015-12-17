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

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ProductFlavorCombo;
import com.android.build.gradle.managed.AndroidConfig;
import com.android.build.gradle.managed.BuildType;
import com.android.build.gradle.managed.ProductFlavor;
import com.android.build.gradle.ndk.internal.NdkNamingScheme;
import com.android.utils.StringHelper;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.nativeplatform.NativeBinarySpec;

import java.io.File;
import java.util.List;

/**
 * Plugin for compiling native source code to create a shared object.
 */
public class StandaloneNdkComponentModelPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NdkComponentModelPlugin.class);
    }

    public static class Rules extends RuleSource {

        @Validate
        public static void validateCompileSdkVersion(AndroidConfig androidConfig) {
            checkState(
                    androidConfig.getCompileSdkVersion() != null &&
                            !androidConfig.getCompileSdkVersion().isEmpty(),
                    "compileSdkVersion is not specified.");
        }

        @Mutate
        public static void copyOutputs(
                final ModelMap<Task> tasks,
                ModelMap<AndroidBinary> androidSpecs,
                @Path("buildDir") final File buildDir) {
            for (AndroidBinary androidBinary : androidSpecs.values()) {
                for (final NativeBinarySpec nativeBinary : ((DefaultAndroidBinary) androidBinary).getNativeBinaries()) {
                    final String copyTaskName = NdkNamingScheme.getTaskName(nativeBinary, "copy", "Output");
                    tasks.create(
                            copyTaskName,
                            Copy.class,
                            new Action<Copy>() {
                                @Override
                                public void execute(Copy copy) {
                                    copy.from(new File(buildDir,
                                            NdkNamingScheme.getOutputDirectoryName(nativeBinary)));
                                    copy.into(new File(buildDir,
                                            NdkNamingScheme
                                                    .getStandaloneOutputDirectoryName(nativeBinary)));
                                    copy.dependsOn(NdkNamingScheme.getNdkBuildTaskName(nativeBinary));
                                }
                            });
                    dependsOn(tasks, getAssembleTaskName(androidBinary), copyTaskName);
                }
            }
        }

        // Create assemble tasks for each variant, build types and product flavors.

        // TODO: These should be factored out into AndroidComponentModelPlugin.
        // This requires significant changes AndroidTaskRegistry in order for it to work with
        // experimental plugin.
        @Mutate
        public static void createAssembleTasksForBuildTypes(
                ModelMap<Task> tasks,
                @Path("android.buildTypes") final ModelMap<BuildType> buildTypes) {
            for (final Named buildType : buildTypes.values()) {
                createAssembleTask(tasks, buildType);
                dependsOn(tasks, "assemble", getAssembleTaskName(buildType));
            }
        }

        @Mutate
        public static void createAssembleTasksForProductFlavors(
                ModelMap<Task> tasks,
                @Path("android.productFlavors") ModelMap<ProductFlavor> flavors) {
            if (!flavors.isEmpty()) {
                for (final Named productFlavor : flavors.values()) {
                    createAssembleTask(tasks, productFlavor);
                    dependsOn(tasks, "assemble", getAssembleTaskName(productFlavor));
                }
            }
        }

        /**
         * Create assemble tasks for each AndroidBinary and configure their dependencies
         */
        @Mutate
        public static void createAssembleTasksForBinaries(
                ModelMap<Task> tasks,
                ModelMap<AndroidBinary> binaries) {
            for(final AndroidBinary binary : binaries.values()) {
                String binaryAssembleTaskName = getAssembleTaskName(binary);
                if (!binary.getProductFlavors().isEmpty()) {
                    createAssembleTask(tasks, binary);
                    dependsOn(tasks, getAssembleTaskName(binary.getBuildType()),
                            binaryAssembleTaskName);
                    for (ProductFlavor flavor :binary.getProductFlavors()) {
                        dependsOn(tasks, getAssembleTaskName(flavor), binaryAssembleTaskName);
                    }

                    if (binary.getProductFlavors().size() > 1) {
                        createAssembleTaskForFlavorCombo(tasks, binary.getProductFlavors());
                        dependsOn(tasks, getAssembleTaskName(binary.getProductFlavors()), binaryAssembleTaskName);
                    }
                }

                tasks.named(binaryAssembleTaskName, new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        task.dependsOn(binary);
                    }
                });
            }
        }

        private static void dependsOn(
                @NonNull final ModelMap<Task> tasks,
                @NonNull final String dependee,
                @NonNull final String dependent) {
            tasks.named(dependee, new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(dependent);
                }
            });
        }

        @NonNull
        private static String getAssembleTaskName(@NonNull Named dimension) {
            return "assemble" + StringHelper.capitalize(dimension.getName());
        }

        @NonNull
        private static String getAssembleTaskName(@NonNull List<? extends Named> dimensions) {
            return "assemble"
                    + StringHelper.capitalize(ProductFlavorCombo.getFlavorComboName(dimensions));
        }

        private static void createAssembleTaskForFlavorCombo(
                @NonNull ModelMap<Task> tasks,
                @NonNull final List<? extends Named> dimensions) {
            final String flavorCombo = ProductFlavorCombo.getFlavorComboName(dimensions);
            String taskName = getAssembleTaskName(dimensions);
            tasks.create(
                    taskName,
                    new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.setDescription(
                                    "Assembles all builds for flavor combination: " + flavorCombo);
                            task.setGroup(BasePlugin.BUILD_GROUP);
                        }
                    });
        }

        private static void createAssembleTask(
                @NonNull ModelMap<Task> tasks,
                @NonNull final Named dimension) {
            String taskName = getAssembleTaskName(dimension);
            tasks.create(
                    taskName,
                    new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.setDescription(
                                    "Assembles all " + dimension.getName() + " builds.");
                            task.setGroup(BasePlugin.BUILD_GROUP);
                        }
                    });
        }
    }
}
