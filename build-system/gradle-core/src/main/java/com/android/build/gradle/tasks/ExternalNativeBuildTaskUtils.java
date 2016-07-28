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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.model.CoreExternalNativeBuild;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.BuildCommandException;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfo;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for dealing with external native build tasks.
 */
public class ExternalNativeBuildTaskUtils {
    /**
     * Utility function that takes an ABI string and returns the corresponding output folder. Output
     * folder is where build artifacts are placed.
     */
    @NonNull
    static File getOutputFolder(@NonNull File jsonFolder, @NonNull String abi) {
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
    public static List<File> getOutputJsons(@NonNull File jsonFolder,
            @NonNull Collection<String> abis) {
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
    static NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull File json,
            @NonNull String groupName) throws IOException {
        checkArgument(!Strings.isNullOrEmpty(groupName),
                "group name missing in", json);

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .create();
        List<String> lines = Files.readLines(json, Charsets.UTF_8);
        NativeBuildConfigValue config = gson.fromJson(Joiner.on("\n").join(lines),
                NativeBuildConfigValue.class);
        checkNotNull(config.libraries);
        for (NativeLibraryValue library : config.libraries.values()) {
            library.groupName = groupName;
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
            configValues.add(getNativeBuildConfigValue(json, groupName));
        }
        return configValues;
    }

    /**
     * Return true if we should regenerate out-of-date JSON files.
     */
    public static boolean shouldRegenerateOutOfDateJsons(@NonNull Project project) {
        return AndroidGradleOptions.buildModelOnly(project)
                || AndroidGradleOptions.buildModelOnlyAdvanced(project)
                || AndroidGradleOptions.invokedFromIde(project)
                || AndroidGradleOptions.refreshExternalNativeModel(project);
    }

    public static boolean isExternalNativeBuildEnabled(@NonNull CoreExternalNativeBuild config) {
        return (config.getNdkBuild().getPath() != null)
                || (config.getCmake().getPath() != null);
    }

    public static class ExternalNativeBuildProjectPathResolution {
        @Nullable
        public final String errorText;
        @Nullable
        public final NativeBuildSystem buildSystem;
        @Nullable
        public final File makeFile;

        private ExternalNativeBuildProjectPathResolution(
                @Nullable NativeBuildSystem buildSystem,
                @Nullable File makeFile,
                @Nullable String errorText) {
            checkArgument(makeFile == null || buildSystem != null,
                    "Expected path and buildSystem together, no taskClass");
            checkArgument(makeFile != null || buildSystem == null,
                    "Expected path and buildSystem together, no path");
            checkArgument(makeFile == null || errorText == null,
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

        Map<NativeBuildSystem, File> externalProjectPaths = getExternalBuildExplicitPaths(config);
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
     * @throws BuildCommandException when the build failed.
     */
    @NonNull
    public static String executeBuildProcessAndLogError(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ProcessInfo process)
            throws BuildCommandException {
        ExecuteBuildProcessLogger logger =
                new ExecuteBuildProcessLogger(androidBuilder.getLogger());
        try {
            androidBuilder.executeProcess(process, new LoggedProcessOutputHandler(logger))
                    .rethrowFailure().assertNormalExitValue();
            return logger.getOutput();
        } catch (ProcessException e) {
            // Also, add process output to the process exception so that it can be analyzed by
            // caller
            String combinedMessage = String.format("%s\n%s", e.getMessage(), logger.getOutput());
            throw new BuildCommandException(combinedMessage);
        }
    }

    private static class ExecuteBuildProcessLogger implements ILogger {

        @SuppressWarnings("StringBufferField")
        private final StringBuilder output = new StringBuilder();
        private final ILogger logger;

        private ExecuteBuildProcessLogger(ILogger logger) {
            this.logger = logger;
        }

        private String getOutput() {
            return this.output.toString();
        }

        @Override
        public void error(
                @Nullable Throwable t,
                @Nullable String msgFormat,
                Object... args) {
            if (msgFormat != null) {
                output.append(msgFormat);
            }
            logger.error(t, msgFormat, args);
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            // executeProcess doesn't currently output to warning. Capture it anyway. If this
            // changes later don't want to lose information.
            output.append(msgFormat);
            logger.warning(msgFormat, args);
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            // Cannot String.format(msgFormat, args) or printf or similar because compiler output
            // may produce msgFormat with embedded percent (%)
            output.append(msgFormat);
            logger.info(msgFormat, args);
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            // executeProcess doesn't currently output to verbose. Capture it anyway. If this
            // changes later don't want to lose information.
            output.append(msgFormat);
            logger.verbose(msgFormat, args);
        }
    }
}
