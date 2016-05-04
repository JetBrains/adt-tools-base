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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    /**
     * Utility function that takes an ABI string and returns the corresponding output folder. Output
     * folder is where build artifacts are placed.
     */
    private static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(jsonFolder, abi);
    }

    /**
     * Utility function that gets the name of the output JSON for a particular ABI.
     */
    public static File getOutputJson(File jsonFolder, String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "android_gradle_build.json");
    }


    public static List<File> getOutputJsons(@NonNull File jsonFolder, @NonNull Set<String> abis) {
        List<File> outputs = Lists.newArrayList();
        for (String abi : abis) {
            outputs.add(getOutputJson(jsonFolder, abi));
        }
        return outputs;
    }

    /**
     * Deserialize a JSON file into NativeBuildConfigValue. Emit task-specific exception if there is
     * an issue.
     */
    public static NativeBuildConfigValue getNativeBuildConfigValue(@NonNull File json) {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                    .create();
            List<String> lines = Files.readLines(json, Charsets.UTF_8);
            return gson.fromJson(Joiner.on("\n").join(lines), NativeBuildConfigValue.class);
        } catch (Throwable e) {
            throw new GradleException(
                    String.format("Could not parse '%s'", json), e);
        }
    }

    /**
     * Check whether the given JSON file should be regenerated.
     */
    public static boolean shouldRebuildJson(@NonNull File json) {
        if (!json.exists()) {
            // deciding that JSON file should be rebuilt because it doesn't exist
            return true;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValue config = getNativeBuildConfigValue(json);
        if (config.buildFiles != null) {
            long jsonLastModified = json.lastModified();
            for (File buildFile : config.buildFiles) {
                if (!buildFile.exists()) {
                    throw new GradleException(
                            String.format("Expected build file %s to exist", buildFile));
                }
                if (buildFile.lastModified() > jsonLastModified) {
                    // deciding that JSON file should be rebuilt because is older than buildFile
                    return true;
                }
            }
        }

        // deciding that JSON file should not be rebuilt because it is up-to-date
        return false;
    }

    /**
     * Return abi filters, don't accept "all"
     */
    public static Set<String> getAbiFilters(Set<String> abis) {
        if (abis == null || abis.size() == 0) {
            abis = Sets.newHashSet();
            for (Abi abi : NdkHandler.getAbiList()) {
                abis.add(abi.getName());
            }
        }
        if (abis.contains("all")) {
            throw new GradleException(
                    "expected abis to be specific, at least one is 'all'");
        }
        return abis;
    }

    public static class ExternalNativeBuildProjectPathResolution {
        public final String errorText;
        public final Class<? extends ExternalNativeBuildJsonModelGenerationBaseTask> taskClass;
        public final File path;

        ExternalNativeBuildProjectPathResolution(
                Class<? extends ExternalNativeBuildJsonModelGenerationBaseTask> taskClass,
                File path,
                String errorText) {
            if (path != null && taskClass == null) {
                throw new IllegalArgumentException(
                        "Expected path and taskClass together, no taskClass");
            }

            if (path == null && taskClass != null) {
                throw new IllegalArgumentException("Expected path and taskClass together, no path");
            }

            if (path != null && errorText != null) {
                throw new IllegalArgumentException("Expected path or error but both existed");
            }

            this.taskClass = taskClass;
            this.path = path;
            this.errorText = errorText;
        }
    }

    /**
     * Resolve the path of any native build project.
     * There are two types of path: explicit and light-up. Explicit means the path exists in the
     * Gradle (for example, cmake.path). Light-up means there is no path in the Gradle but there
     * is an external build in the project directory.
     *
     * Generally, paths in the DSL take precedence since they are overtly specified by the user.
     * When there is no path in the DSL then light-up occurs when there is a CMakeLists.txt or
     * Android.mk file on the disk.
     *
     * Generally, there may not be more than one path of a given type. It is an error for there to
     * be two paths in the DSL (ie cmake.path and ndkBuild.path). If there are two light-up paths
     * (ie ./Android.mk and ./CMakeLists.txt) then this is also an error.
     *
     * @param projectFolder -- the folder of the build.gradle file
     * @param config -- the AndroidConfig
     * @return Path resolution.
     */
    @NonNull
    public static ExternalNativeBuildProjectPathResolution getProjectPath(File projectFolder,
            AndroidConfig config) {
        // Path discovery logic:
        // If there is exactly 1 path in the DSL, then use it.
        // If there are more than 1, then that is an error. The user has specified both cmake and
        //    ndkBuild in the same project.

        // TODO(chiur): Default path is temporarily disabled, but some of the logic that was used
        // for to handle default paths was kept.  If we decide to keep default disabled, we should
        // simplify the logic in this function.

        Map<Class, File> externalProjectPaths = ExternalNativeBuildTaskUtils
                .getExternalBuildExplicitPaths(config);
        if (externalProjectPaths.size() > 1) {
            return new ExternalNativeBuildProjectPathResolution(
                    null, null, "More than one externalNativeBuild path specified");
        }

        if (externalProjectPaths.isEmpty()) {
            // No external projects present.
            return new ExternalNativeBuildProjectPathResolution(null, null, null);
        }

        return new ExternalNativeBuildProjectPathResolution(
                externalProjectPaths.keySet().iterator().next(),
                externalProjectPaths.values().iterator().next(),
                null);
    }

    /**
     * @return a map of generate task to path from DSL. Zero entries means there are no paths in
     * the DSL. Greater than one entries means that multiple paths are specified, this is an error.
     */
    private static Map<Class, File> getExternalBuildExplicitPaths(AndroidConfig config) {
        Map<Class, File> map = new HashMap<>();
        File cmake = config.getExternalNativeBuild().getCmake().getPath();
        File ndkBuild = config.getExternalNativeBuild().getNdkBuild().getPath();

        if (cmake != null) {
            map.put(CmakeJsonModelGenerationTask.class, cmake);
        }
        if (ndkBuild != null) {
            map.put(NdkBuildJsonModelGenerationTask.class, ndkBuild);
        }
        return map;
    }

    /**
     * @return a map of paths from predefined light-up locations. Zero entries means there are no
     * light up paths. Greater than one entries means that multiple paths are specified, this is an
     * error.
     */
    private static Map<Class, File> getExternalBuildLightUpPaths(File projectFolder) {
        Map<Class, File> map = new HashMap<>();
        File cmake = new File(projectFolder, "CMakeLists.txt");
        File ndkBuild = new File(projectFolder, "Android.mk");
        if (cmake.exists()) {
            map.put(CmakeJsonModelGenerationTask.class, cmake.getParentFile());
        }
        if (ndkBuild.exists()) {
            map.put(NdkBuildJsonModelGenerationTask.class, ndkBuild);
        }
        return map;
    }
}
