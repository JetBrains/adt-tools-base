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

package com.android.build.gradle.integration.common.utils;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeSettings;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper class for {@link NativeAndroidProject}.
 */
public class NativeModelHelper {

    /**
     * Return the NativeArtifact with the specified name.
     */
    @NonNull
    public static NativeArtifact getArtifact(
            @NonNull NativeAndroidProject project,
            @NonNull String name) {
        Collection<NativeArtifact> nativeLibraries =
                project.getArtifacts().stream().filter(a->a.getName().equals(name))
                        .collect(Collectors.toList());
        assertThat(nativeLibraries).hasSize(1);
        return nativeLibraries.iterator().next();
    }

    /**
     * Return a map of C flags for each NativeFolder and NativeFile.
     *
     * The key is the folderPath of a NativeFolder or filePath of a NativeFile.
     * The value is the list of flags.
     */
    @NonNull
    public static Map<File, List<String>> getCFlags(
            @NonNull NativeAndroidProject project,
            @NonNull NativeArtifact artifact) {
        Map<File, String> settingsMap = Maps.newHashMap();

        for (NativeFolder nativeFolder : artifact.getSourceFolders()) {
            String setting = nativeFolder.getPerLanguageSettings().get("c");
            if (setting != null) {
                settingsMap.put(nativeFolder.getFolderPath(), setting);
            }
        }
        for (NativeFile nativeFile : artifact.getSourceFiles()) {
            if (nativeFile.getFilePath().getName().endsWith(".c")) {
                String setting = nativeFile.getSettingsName();
                settingsMap.put(nativeFile.getFilePath(), setting);
            }
        }


        Map<File, List<String>> flagsMap = Maps.newHashMap();
        for (Map.Entry<File, String> entry : settingsMap.entrySet()) {
            flagsMap.put(entry.getKey(), findFlags(project, entry.getValue()));
        }
        return flagsMap;
    }

    /**
     * Return the C flags for all NativeFolder and NativeFile.
     * Flags in all NativeFolder and NativeFile in the NativeArtifact is flatten into a single list.
     */
    public static List<String> getFlatCFlags(
            @NonNull NativeAndroidProject project,
            @NonNull NativeArtifact artifact) {
        return getCFlags(project, artifact).values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static List<String> findFlags(
            @NonNull NativeAndroidProject project,
            @NonNull String settingName) {
        Collection<NativeSettings> settings = project.getSettings();
        Optional<NativeSettings> setting = settings.stream()
                .filter(s -> s.getName().equals(settingName))
                .findFirst();
        assertThat(setting).isPresent();
        return setting.get().getCompilerFlags();
    }
}
