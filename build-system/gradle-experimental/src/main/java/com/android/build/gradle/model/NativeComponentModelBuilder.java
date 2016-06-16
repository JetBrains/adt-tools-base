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

import static com.android.build.gradle.model.ModelConstants.EXTERNAL_BUILD_CONFIG;
import static com.android.build.gradle.model.ModelConstants.NATIVE_BUILD_SYSTEMS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.model.NativeAndroidProjectImpl;
import com.android.build.gradle.internal.model.NativeArtifactImpl;
import com.android.build.gradle.internal.model.NativeFileImpl;
import com.android.build.gradle.internal.model.NativeFolderImpl;
import com.android.build.gradle.internal.model.NativeSettingsImpl;
import com.android.build.gradle.internal.model.NativeToolchainImpl;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.ndk.internal.NativeCompilerArgsUtil;
import com.android.builder.Version;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.utils.StringHelper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.api.Project;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builder for the {@link NativeAndroidProject} model.
 */
public class NativeComponentModelBuilder implements ToolingModelBuilder {

    @NonNull
    private final ModelRegistry registry;
    @NonNull
    private final Map<List<String>, NativeSettings> settingsMap = Maps.newHashMap();
    private int settingIndex = 0;
    private NativeBuildConfig config;
    private List<String> buildSystems;

    public NativeComponentModelBuilder(@NonNull ModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(NativeAndroidProject.class.getName());
    }

    /**
     * Initialize private members.
     */
    private void initialize() {
        config = registry.realize(
                new ModelPath(EXTERNAL_BUILD_CONFIG),
                ModelType.of(NativeBuildConfig.class));
        buildSystems = registry.realize(
                new ModelPath(NATIVE_BUILD_SYSTEMS),
                ModelTypes.list(ModelType.of(String.class)));
        settingIndex = 0;
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        initialize();

        List<NativeArtifact> artifacts = createNativeArtifacts();
        List<NativeToolchain> toolchains = createNativeToolchains();
        Collection<NativeSettings> settings = ImmutableList.copyOf(settingsMap.values());
        Map<String, String> extensions = createFileExtensionMap();
        return new NativeAndroidProjectImpl(
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                project.getName(),
                ImmutableList.copyOf(config.getBuildFiles()),
                artifacts,
                toolchains,
                settings,
                extensions,
                buildSystems,
                Version.BUILDER_NATIVE_MODEL_API_VERSION);

    }

    private List<NativeArtifact> createNativeArtifacts() {
        List<NativeArtifact> artifacts = Lists.newArrayList();

        for (NativeLibrary lib : config.getLibraries()) {
            List<NativeFolder> folders = lib.getFolders().stream()
                    .map(src -> {
                        checkNotNull(src.getSrc());
                        return new NativeFolderImpl(
                                src.getSrc(),
                                ImmutableMap.of(
                                        "c", getSettingsName(convertFlagFormat(src.getcFlags())),
                                        "c++", getSettingsName(convertFlagFormat(src.getCppFlags()))),
                                src.getWorkingDirectory());})
                    .collect(Collectors.toList());
            List<NativeFile> files = lib.getFiles().stream()
                    .map(src -> {
                        checkNotNull(src.getSrc());
                        return new NativeFileImpl(
                                src.getSrc(),
                                getSettingsName(convertFlagFormat(src.getFlags())),
                                src.getWorkingDirectory());})
                    .collect(Collectors.toList());
            checkNotNull(lib.getToolchain());
            checkNotNull(lib.getAssembleTaskName());
            checkNotNull(lib.getOutput());
            checkNotNull(lib.getAbi());
            checkNotNull(lib.getArtifactName());
            NativeArtifact artifact = new NativeArtifactImpl(
                    lib.getName(),
                    lib.getToolchain(),
                    Strings.nullToEmpty(lib.getGroupName()),
                    lib.getAssembleTaskName(),
                    folders,
                    files,
                    ImmutableList.copyOf(lib.getExportedHeaders()),
                    lib.getOutput(),
                    lib.getAbi(),
                    lib.getArtifactName());
            artifacts.add(artifact);
        }
        return artifacts;
    }

    @NonNull
    private static List<String> convertFlagFormat(@Nullable String flags) {
        if (flags == null) {
            return Lists.newArrayList();
        }
        return NativeCompilerArgsUtil.transform(StringHelper.tokenizeString(flags));
    }

    private String getSettingsName(@NonNull List<String> flags) {
        // Copy flags to ensure it is serializable.
        List<String> flagsCopy = ImmutableList.copyOf(flags);
        NativeSettings setting = settingsMap.get(flags);
        if (setting == null) {
            setting = new NativeSettingsImpl("setting" + settingIndex, flagsCopy);
            settingsMap.put(flagsCopy, setting);
            settingIndex++;
        }
        return setting.getName();
    }

    private List<NativeToolchain> createNativeToolchains() {
        return config.getToolchains().values().stream()
                .map(toolchain -> new NativeToolchainImpl(
                        toolchain.getName(),
                        toolchain.getCCompilerExecutable(),
                        toolchain.getCppCompilerExecutable())).collect(Collectors.toList());
    }

    private Map<String, String> createFileExtensionMap() {
        Map<String, String> extensions = Maps.newHashMap();
        for (String ext : config.getcFileExtensions()) {
            extensions.put(ext, "c");
        }
        for (String ext : config.getCppFileExtensions()) {
            extensions.put(ext, "c++");
        }
        return extensions;
    }
}
