/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.model;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.ndk.internal.NativeCompilerArgsUtil;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builder for the custom Native Android model.
 */
public class NativeModelBuilder implements ToolingModelBuilder {

    @NonNull
    private final VariantManager variantManager;

    public NativeModelBuilder(
            @NonNull VariantManager variantManager) {
        this.variantManager = variantManager;
    }

    @Override
    public boolean canBuild(@NonNull String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(NativeAndroidProject.class.getName());
    }

    @Nullable
    @Override
    public Object buildAll(String modelName, @NonNull Project project) {
        return NativeAndroidProjectBuilder.build(project, variantManager);
    }

    /**
     * Build information needed for NativeAndroidProjectImpl
     */
    private static class NativeAndroidProjectBuilder {
        private int settingIndex = 0;
        @NonNull private final Set<File> buildFiles = Sets.newHashSet();
        @NonNull private final Map<String, String> extensions = Maps.newHashMap();
        @NonNull private final List<NativeArtifact> artifacts = Lists.newArrayList();
        @NonNull private final List<NativeToolchain> toolChains = Lists.newArrayList();
        @NonNull private final List<NativeBuildConfigValue> configValues = Lists.newArrayList();
        @NonNull private final Map<List<String>, NativeSettings> settingsMap = Maps.newHashMap();

        @Nullable
        private static NativeAndroidProject build(
                @NonNull Project project,
                @NonNull VariantManager variantManager) {

            NativeAndroidProjectBuilder info = new NativeAndroidProjectBuilder();

            Set<String> buildSystems = Sets.newHashSet();
            for (BaseVariantData<? extends BaseVariantOutputData> variantData
                    : variantManager.getVariantDataList()) {
                VariantScope scope = variantData.getScope();
                ExternalNativeJsonGenerator generator = scope.getExternalNativeJsonGenerator();
                if (generator != null) {
                    buildSystems.add(generator.getNativeBuildSystem().getName());
                }
                for (NativeBuildConfigValue configValue :
                        scope.getExternalNativeBuildConfigValues()) {

                    // Record build files
                    if (configValue.buildFiles != null) {
                        info.buildFiles.addAll(configValue.buildFiles);
                    }

                    // Record new tool chains
                    if (configValue.toolchains != null) {
                        for (String toolchainName : configValue.toolchains.keySet()) {
                            info.toolChains.add(new NativeToolchainImpl(
                                    toolchainName,
                                    configValue.toolchains.get(toolchainName)
                                            .cCompilerExecutable,
                                    configValue.toolchains.get(toolchainName)
                                            .cppCompilerExecutable));
                        }
                    }

                    // Record artifacts
                    if (configValue.libraries != null) {
                        for (String name : configValue.libraries.keySet()) {
                            NativeLibraryValue library = configValue.libraries.get(name);
                            info.artifacts.add(info.createNativeArtifact(
                                    name,
                                    library));
                        }
                    }

                    // Record extensions
                    if (configValue.cFileExtensions != null) {
                        for (String ext : configValue.cFileExtensions) {
                            info.extensions.put(ext, "c");
                        }
                    }
                    if (configValue.cppFileExtensions != null) {
                        for (String ext : configValue.cppFileExtensions) {
                            info.extensions.put(ext, "c++");
                        }
                    }

                    info.configValues.add(configValue);
                }
            }

            // If there are no build files (therefore no native configurations) don't return a model
            if (info.buildFiles.isEmpty()) {
                return null;
            }

            return new NativeAndroidProjectImpl(
                    com.android.builder.Version.ANDROID_GRADLE_PLUGIN_VERSION,
                    project.getName(),
                    info.buildFiles,
                    info.artifacts,
                    info.toolChains,
                    ImmutableList.copyOf(info.settingsMap.values()),
                    info.extensions,
                    buildSystems,
                    com.android.builder.Version.BUILDER_MODEL_API_VERSION);
        }

        @Nullable
        private NativeArtifact createNativeArtifact(
                @NonNull String name,
                @NonNull NativeLibraryValue library) {
            List<NativeFolder> folders = new ArrayList<>();
            if (library.folders != null) {
                folders = library.folders.stream().map(src -> {
                    Preconditions.checkNotNull(src.src);
                    return new NativeFolderImpl(
                            src.src,
                            ImmutableMap.of(
                                    "c", getSettingsName(convertFlagFormat(
                                            src.cFlags != null ? src.cFlags : "")),
                                    "c++", getSettingsName(convertFlagFormat(
                                            src.cppFlags != null ? src.cppFlags : ""))),
                            src.workingDirectory);
                })
                        .collect(Collectors.toList());
            }
            List<NativeFile> files = new ArrayList<>();
            if (library.files != null) {
                files = library.files.stream().map(src -> {
                    Preconditions.checkNotNull(src.src);
                    return new NativeFileImpl(
                            src.src,
                            getSettingsName(convertFlagFormat(src.flags != null ? src.flags : "")),
                            src.workingDirectory);
                })
                        .collect(Collectors.toList());
            }
            Preconditions.checkNotNull(library.toolchain);
            Preconditions.checkNotNull(library.output);
            Collection<File> exportedHeaders = new ArrayList<>();
            if (library.exportedHeaders != null) {
                exportedHeaders = ImmutableList.copyOf(library.exportedHeaders);
            }
            checkState(!Strings.isNullOrEmpty(library.groupName), "groupName missing");
            checkState(!Strings.isNullOrEmpty(library.abi), "abi missing");
            checkState(!Strings.isNullOrEmpty(library.artifactName), "artifactName missing");
            return new NativeArtifactImpl(
                    name,
                    library.toolchain,
                    library.groupName,
                    "",
                    folders,
                    files,
                    exportedHeaders,
                    library.output,
                    library.abi,
                    library.artifactName);
        }

        @NonNull
        private static List<String> convertFlagFormat(@NonNull String flags) {
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
    }
}
