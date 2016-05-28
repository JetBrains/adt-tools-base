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
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfo;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
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
    @NonNull
    private static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(jsonFolder, abi);
    }

    /**
     * Utility function that gets the name of the output JSON for a particular ABI.
     */
    @NonNull
    public static File getOutputJson(@NonNull File jsonFolder, @NonNull String abi) {
        return new File(getOutputFolder(jsonFolder, abi), "android_gradle_build.json");
    }


    @NonNull
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
    @NonNull
    private static NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull File json,
            @NonNull String groupName) throws IOException {

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .create();
        List<String> lines = Files.readLines(json, Charsets.UTF_8);
        NativeBuildConfigValue config = gson.fromJson(Joiner.on("\n").join(lines),
                NativeBuildConfigValue.class);
        if (config.libraries != null) {
            for (NativeLibraryValue library : config.libraries.values()) {
                library.groupName = groupName;
            }
        }
        return config;
    }

    /**
     * Deserialize a JSON files into NativeBuildConfigValue.
     */
    @NonNull
    public static Collection<NativeBuildConfigValue> getNativeBuildConfigValues(
            @NonNull Collection<File> jsons,
            @NonNull String groupName) throws IOException {
        List<NativeBuildConfigValue> configValues = Lists.newArrayList();
        for (File json : jsons) {
            configValues.add(
                ExternalNativeBuildTaskUtils.getNativeBuildConfigValue(json, groupName));
        }
        return configValues;
    }

    /**
     * Check whether the given JSON file should be regenerated.
     */
    public static boolean shouldRebuildJson(@NonNull File json,
            @NonNull String groupName) throws IOException {
        if (!json.exists()) {
            // deciding that JSON file should be rebuilt because it doesn't exist
            return true;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValue config = getNativeBuildConfigValue(json, groupName);
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
    @NonNull
    public static Set<String> getAbiFilters(@Nullable Set<String> abis) {
        if (abis == null || abis.isEmpty()) {
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

    /**
     * Return true if we should regenerate out-of-date JSON files.
     */
    public static boolean shouldRegenerateOutOfDateJsons(Project project) {
        return AndroidGradleOptions.buildModelOnly(project)
                || AndroidGradleOptions.buildModelOnlyAdvanced(project)
                || AndroidGradleOptions.invokedFromIde(project)
                || AndroidGradleOptions.refreshExternalNativeModel(project);
    }

    public static class ExternalNativeBuildProjectPathResolution {
        @Nullable
        public final String errorText;
        @Nullable
        public final NativeBuildSystem buildSystem;
        @Nullable
        public final File makeFile;

        ExternalNativeBuildProjectPathResolution(
                @Nullable NativeBuildSystem buildSystem,
                @Nullable File makeFile,
                @Nullable String errorText) {
            Preconditions.checkArgument(makeFile == null || buildSystem != null,
                    "Expected path and buildSystem together, no taskClass");
            Preconditions.checkArgument(makeFile != null || buildSystem == null,
                    "Expected path and buildSystem together, no path");
            Preconditions.checkArgument(makeFile == null || errorText == null,
                    "Expected path or error but both existed");
            this.buildSystem = buildSystem;
            this.makeFile = makeFile;
            this.errorText = errorText;
        }
    }

    /**
     * Resolve the path of any native build project.
     * @param config -- the AndroidConfig
     * @return Path resolution.
     */
    @NonNull
    public static ExternalNativeBuildProjectPathResolution getProjectPath(
            @NonNull CoreExternalNativeBuild config) {
        // Path discovery logic:
        // If there is exactly 1 path in the DSL, then use it.
        // If there are more than 1, then that is an error. The user has specified both cmake and
        //    ndkBuild in the same project.

        Map<NativeBuildSystem, File> externalProjectPaths = ExternalNativeBuildTaskUtils
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
    @NonNull
    private static Map<NativeBuildSystem, File> getExternalBuildExplicitPaths(
            @NonNull CoreExternalNativeBuild config) {
        Map<NativeBuildSystem, File> map = new EnumMap<>(NativeBuildSystem.class);
        File cmake = config.getCmake().getPath();
        File ndkBuild = config.getNdkBuild().getPath();

        if (cmake != null) {
            map.put(NativeBuildSystem.CMAKE, cmake);
        }
        if (ndkBuild != null) {
            map.put(NativeBuildSystem.NDK_BUILD, ndkBuild);
        }
        return map;
    }


    /**
     * Execute an external process and log the result in the case of a process exceptions.
     * Returns the info part of the log so that it can be parsed by ndk-build parser;
     * @throws ProcessException when the build failed.
     */
    @NonNull
    public static String executeBuildProcessAndLogError(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ProcessInfo process)
            throws ProcessException {
        final StringBuilder all = new StringBuilder();
        final StringBuilder info = new StringBuilder();
        try {
            androidBuilder.executeProcess(process,
                    new LoggedProcessOutputHandler(
                            new ExecuteBuildProcessLogger(all, info, androidBuilder.getLogger())))
                    .rethrowFailure().assertNormalExitValue();
            return info.toString();
        } catch (ProcessException e) {
            // In the case of error, print to STDERR so it can be seen by Android Studio and
            // from command-line build
            System.err.print(all.toString());
            throw e;
        }
    }

    private static class ExecuteBuildProcessLogger implements ILogger {

        private final StringBuilder all;
        private final StringBuilder info;
        private final ILogger logger;

        public ExecuteBuildProcessLogger(StringBuilder all, StringBuilder info, ILogger logger) {
            this.all = all;
            this.info = info;
            this.logger = logger;
        }

        @Override
        public void error(
                @Nullable Throwable t,
                @Nullable String msgFormat,
                Object... args) {
            if (msgFormat != null) {
                all.append(msgFormat);
            }
            logger.error(t, msgFormat, args);
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            all.append(msgFormat);
            logger.warning(msgFormat, args);
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            // Cannot String.format(msgFormat, args) or printf or similar because compiler output
            // may produce msgFormat with embedded percent (%)
            all.append(msgFormat);
            info.append(msgFormat);
            logger.info(msgFormat, args);
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            all.append(msgFormat);
            logger.verbose(msgFormat, args);
        }
    }
}
