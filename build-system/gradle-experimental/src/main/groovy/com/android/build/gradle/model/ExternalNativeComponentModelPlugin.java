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

import static com.android.build.gradle.model.ModelConstants.ARTIFACTS;
import static com.android.build.gradle.model.ModelConstants.EXTERNAL_BUILD_CONFIG;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.build.gradle.internal.dependency.ArtifactContainer;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.gson.FileGsonTypeAdaptor;
import com.android.build.gradle.internal.gson.NativeBuildConfigValue;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.utils.StringHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.Exec;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.inject.Inject;

/**
 * Plugin for importing projects built with external tools into Android Studio.
 */
public class ExternalNativeComponentModelPlugin implements Plugin<Project> {

    public static final String COMPONENT_NAME = "androidNative";

    @NonNull
    private final ToolingModelBuilderRegistry toolingRegistry;

    @NonNull
    private final ModelRegistry modelRegistry;

    @Inject
    private ExternalNativeComponentModelPlugin(
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        toolingRegistry.register(new NativeComponentModelBuilder(modelRegistry));
    }

    public static class Rules extends RuleSource {
        @ComponentType
        public static void defineComponentType(
                ComponentTypeBuilder<ExternalNativeComponentSpec> builder) {
            builder.defaultImplementation(ExternalNativeComponentSpec.class);
        }

        @BinaryType
        public static void defineBinaryType(BinaryTypeBuilder<ExternalNativeBinarySpec> builder) {
            builder.defaultImplementation(ExternalNativeBinarySpec.class);
        }

        @Model(EXTERNAL_BUILD_CONFIG)
        public static void createNativeBuildModel(NativeBuildConfig config) {
        }

        /**
         * Parses the JSON file to populate the NativeBuildConfig.
         * Overwrites all existing values in the config.
         *
         * TODO: NativeBuildConfig should not be set if JSON is used.  Verify this and throws an
         * error if any field is set.
         */
        @Finalize
        public static void readJson(
                NativeBuildConfig config,
                ServiceRegistry registry) throws IOException {
            File configFile = config.getConfigFile();
            if (configFile == null) {
                return;
            }

            FileResolver fileResolver = registry.get(FileResolver.class);
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(File.class, new FileGsonTypeAdaptor(fileResolver))
                    .create();

            NativeBuildConfigValue jsonConfig =
                    gson.fromJson(new FileReader(configFile), NativeBuildConfigValue.class);
            jsonConfig.copyTo(config);
        }

        @Mutate
        public static void createExternalNativeComponent(
                ModelMap<ExternalNativeComponentSpec> components,
                final NativeBuildConfig config) {
            components.create(COMPONENT_NAME, new Action<ExternalNativeComponentSpec>() {
                @Override
                public void execute(ExternalNativeComponentSpec component) {
                    component.setConfig(config);
                }
            });
        }

        @ComponentBinaries
        public static void createExternalNativeBinary(
                final ModelMap<ExternalNativeBinarySpec> binaries,
                final ExternalNativeComponentSpec component) {
            for(final NativeLibrary lib : component.getConfig().getLibraries()) {
                binaries.create(lib.getName(), new Action<ExternalNativeBinarySpec>() {
                    @Override
                    public void execute(ExternalNativeBinarySpec binary) {
                        binary.setConfig(lib);
                    }
                });
            }
        }

        @BinaryTasks
        public static void createTasks(ModelMap<Task> tasks, final ExternalNativeBinarySpec binary) {
            tasks.create(
                    "create" + StringHelper.capitalize(binary.getName()),
                    Exec.class,
                    new Action<Exec>() {
                        @Override
                        public void execute(Exec exec) {
                            exec.executable(binary.getConfig().getExecutable());
                            exec.args(binary.getConfig().getArgs());
                        }
                    });
        }

        @Model(ARTIFACTS)
        public static void createNativeLibraryArtifacts(
                ArtifactContainer artifactContainer,
                final NativeBuildConfig config,
                final ModelMap<Task> tasks) {
            for(final NativeLibrary lib : config.getLibraries()) {
                artifactContainer.getNativeArtifacts().create(lib.getName(),
                        new Action<NativeLibraryArtifact>() {
                            @Override
                            public void execute(NativeLibraryArtifact artifacts) {
                                artifacts.getLibraries().add(lib.getOutput());
                                artifacts.setAbi(lib.getAbi());
                                artifacts.setVariantName(lib.getName());
                                artifacts.setLinkage(lib.getOutput().getName().endsWith(".so")
                                        ? NativeDependencyLinkage.SHARED
                                        : NativeDependencyLinkage.STATIC);
                                artifacts.setBuiltBy(
                                        tasks.get(
                                                "create" + StringHelper.capitalize(lib.getName())));
                            }
                        });
            }
        }
    }
}
