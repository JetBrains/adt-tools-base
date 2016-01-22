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

import static com.android.build.gradle.model.ModelConstants.ANDROID_BUILDER;
import static com.android.build.gradle.model.ModelConstants.ANDROID_CONFIG_ADAPTOR;
import static com.android.build.gradle.model.ModelConstants.BINARIES;
import static com.android.build.gradle.model.ModelConstants.COMPONENTS;
import static com.android.build.gradle.model.ModelConstants.EXTRA_MODEL_INFO;
import static com.android.build.gradle.model.ModelConstants.IS_APPLICATION;
import static com.android.build.gradle.model.ModelConstants.NDK_HANDLER;
import static com.android.build.gradle.model.ModelConstants.TASK_MANAGER;

import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.NdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.dependency.NativeDependencyResolveResult;
import com.android.build.gradle.internal.model.ModelBuilder;
import com.android.build.gradle.managed.NdkAbiOptions;
import com.android.build.gradle.model.internal.AndroidBinaryInternal;
import com.android.build.gradle.model.internal.AndroidComponentSpecInternal;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.google.common.collect.Multimap;

import org.gradle.api.Project;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * A ToolingModelBuilder for creating AndroidProject in the component model plugin.
 *
 * It retrieves models from ModelRegistry and uses ModelBuilder to create AndroidProject.
 *
 * This model builder uses Gradle's internal API as a public API for create tooling model from
 * component model is not yet available.
 */
public class ComponentModelBuilder implements ToolingModelBuilder {

    ModelBuilder modelBuilder;
    ModelRegistry registry;


    public ComponentModelBuilder(ModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(AndroidProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (modelBuilder == null) {
            modelBuilder = createModelBuilder();
        }
        return modelBuilder.buildAll(modelName, project);
    }

    /**
     * Return the ModelType for a parameterized Multimap.
     */
    private static <K, V> ModelType<Multimap<K, V>> multimapModelType(
            Class<K> keyClass,
            Class<V> valueClass) {
        return new ModelType.Builder<Multimap<K, V>>() {}
                .where(new ModelType.Parameter<K>() {}, ModelType.of(keyClass))
                .where(new ModelType.Parameter<V>() {}, ModelType.of(valueClass))
                .build();
    }

    private ModelBuilder createModelBuilder() {
        AndroidBuilder androidBuilder = registry.realize(
                new ModelPath(ANDROID_BUILDER),
                ModelType.of(AndroidBuilder.class));
        AndroidComponentSpecInternal componentSpec = registry.realize(
                new ModelPath(COMPONENTS),
                ModelTypes.modelMap(AndroidComponentSpecInternal.class))
                        .get(AndroidComponentModelPlugin.COMPONENT_NAME);
        VariantManager variantManager = componentSpec.getVariantManager();
        TaskManager taskManager = registry.realize(
                new ModelPath(TASK_MANAGER),
                ModelType.of(TaskManager.class));
        AndroidConfig extension = registry.realize(
                new ModelPath(ANDROID_CONFIG_ADAPTOR),
                ModelType.of(AndroidConfig.class));
        ExtraModelInfo extraModelInfo = registry.realize(
                new ModelPath(EXTRA_MODEL_INFO),
                ModelType.of(ExtraModelInfo.class));
        Boolean isApplication = registry.realize(
                new ModelPath(IS_APPLICATION),
                ModelType.of(Boolean.class));
        NdkHandler ndkHandler = registry.realize(
                new ModelPath(NDK_HANDLER),
                ModelType.of(NdkHandler.class));
        ModelMap<AndroidBinaryInternal> binaries = registry.realize(
                new ModelPath(BINARIES),
                ModelTypes.modelMap(AndroidBinaryInternal.class));
        ModelMap<NdkAbiOptions> abiOptions = registry.realize(
                new ModelPath(ModelConstants.ABI_OPTIONS),
                ModelTypes.modelMap(NdkAbiOptions.class));
        Multimap<String, NativeDependencyResolveResult> nativeDependencies = registry.realize(
                new ModelPath(ModelConstants.NATIVE_DEPENDENCIES),
                multimapModelType(String.class, NativeDependencyResolveResult.class));
        Multimap<String, NativeDependencyResolveResult> jniLibsDependencies = registry.realize(
                new ModelPath(ModelConstants.JNILIBS_DEPENDENCIES),
                multimapModelType(String.class, NativeDependencyResolveResult.class));

        return new ModelBuilder(
                androidBuilder,
                variantManager,
                taskManager,
                extension,
                extraModelInfo,
                ndkHandler,
                new ComponentNativeLibraryFactory(
                        binaries, ndkHandler, abiOptions, nativeDependencies, jniLibsDependencies),
                !isApplication,
                AndroidProject.GENERATION_COMPONENT);
    }
}
