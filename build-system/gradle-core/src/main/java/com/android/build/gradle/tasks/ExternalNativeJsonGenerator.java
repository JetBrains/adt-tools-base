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

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.apache.tools.ant.taskdefs.Move;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFiles;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {

    @NonNull
    final String variantName;
    @NonNull
    private final Set<String> abis;
    @NonNull
    final AndroidBuilder androidBuilder;
    @NonNull
    private final File makefile;
    @NonNull
    private final File sdkFolder;
    @NonNull
    private final File ndkFolder;
    @NonNull
    private final File soFolder;
    @NonNull
    private final File objFolder;
    @NonNull
    private final File jsonFolder;
    private final boolean debuggable;
    @NonNull
    private final List<String> buildArguments;
    @NonNull
    private final List<String> cFlags;
    @NonNull
    private final List<String> cppFlags;
    @NonNull
    private final List<File> nativeBuildConfigurationsJsons;

    ExternalNativeJsonGenerator(
            @NonNull String variantName,
            @NonNull Set<String> abis,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File sdkFolder,
            @NonNull File ndkFolder,
            @NonNull File soFolder,
            @NonNull File objFolder,
            @NonNull File jsonFolder,
            @NonNull File makefile,
            boolean debuggable,
            @Nullable List<String> buildArguments,
            @Nullable List<String> cFlags,
            @Nullable List<String> cppFlags) {
        Preconditions.checkArgument(!abis.isEmpty(), "No ABIs specified");
        this.variantName = variantName;
        this.abis = abis;
        this.androidBuilder = androidBuilder;
        this.sdkFolder = sdkFolder;
        this.ndkFolder = ndkFolder;
        this.soFolder = soFolder;
        this.objFolder = objFolder;
        this.jsonFolder = jsonFolder;
        this.makefile = makefile;
        this.debuggable = debuggable;
        this.buildArguments = buildArguments == null ? Lists.newArrayList() : buildArguments;
        this.cFlags = cFlags == null ? Lists.newArrayList() : cFlags;
        this.cppFlags = cppFlags == null ? Lists.newArrayList() : cppFlags;
        this.nativeBuildConfigurationsJsons = ExternalNativeBuildTaskUtils.getOutputJsons(
                jsonFolder,
                abis);
    }

    /**
     * Returns true if platform is windows
     */
    protected static boolean isWindows() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }

    /**
     * Check whether the given JSON file should be regenerated.
     */
    private static boolean shouldRebuildJson(@NonNull File json, @NonNull String groupName)
            throws IOException {
        if (!json.exists()) {
            // deciding that JSON file should be rebuilt because it doesn't exist
            return true;
        }

        // Now check whether the JSON is out-of-date with respect to the build files it declares.
        NativeBuildConfigValue config = ExternalNativeBuildTaskUtils
                .getNativeBuildConfigValue(json, groupName);
        if (config.buildFiles != null) {
            long jsonLastModified = java.nio.file.Files.getLastModifiedTime(
                    json.toPath()).toMillis();
            for (File buildFile : config.buildFiles) {
                if (!buildFile.exists()) {
                    throw new GradleException(
                            String.format("Expected build file %s to exist", buildFile));
                }
                long buildFileLastModified = java.nio.file.Files.getLastModifiedTime(
                        buildFile.toPath()).toMillis();
                if (buildFileLastModified > jsonLastModified) {
                    // deciding that JSON file should be rebuilt because is older than buildFile
                    return true;
                }
            }
        }

        // deciding that JSON file should not be rebuilt because it is up-to-date
        return false;
    }

    public void build() throws IOException, ProcessException {
        buildAndPropagateException(false);
    }

    public void build(boolean forceJsonGeneration) {
        try {
            buildAndPropagateException(forceJsonGeneration);
        } catch (IOException | GradleException e ) {
            androidBuilder.getErrorReporter().handleSyncError(
                    variantName,
                    SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    e.getMessage());
        } catch (ProcessException e) {
            androidBuilder.getErrorReporter().handleSyncError(
                    e.getMessage(),
                    SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION,
                    String.format("executing external native build for %s %s",
                            getNativeBuildSystem().getName(),
                            makefile));
        }
    }

    private void buildAndPropagateException(boolean forceJsonGeneration) throws IOException, ProcessException {
        diagnostic("starting JSON generation");
        diagnostic("bringing JSONs up-to-date");
        for (String abi : abis) {
            File expectedJson = ExternalNativeBuildTaskUtils.getOutputJson(getJsonFolder(), abi);
            ProcessInfoBuilder processBuilder = getProcessBuilder(abi, expectedJson);

            // See whether the current build command matches a previously written build command.
            String currentBuildCommand = processBuilder.toString();
            boolean rebuildDueToMissingPreviousCommand = false;
            File commandFile = new File(
                    ExternalNativeBuildTaskUtils.getOutputFolder(jsonFolder, abi),
                    String.format("%s_build_command.txt", getNativeBuildSystem().getName()));

            boolean rebuildDueToChangeInCommandFile = false;
            if (!commandFile.exists()) {
                rebuildDueToMissingPreviousCommand = true;
            } else {
                String previousBuildCommand =
                        Files.asCharSource(commandFile, Charsets.UTF_8).read();
                if (!previousBuildCommand.equals(currentBuildCommand)) {
                    rebuildDueToChangeInCommandFile = true;
                }
            }
            boolean generateDueToBuildFileChange = shouldRebuildJson(expectedJson, variantName);
            if (forceJsonGeneration
                    || generateDueToBuildFileChange
                    || rebuildDueToMissingPreviousCommand
                    || rebuildDueToChangeInCommandFile) {
                diagnostic("rebuilding JSON %s due to:", expectedJson);
                if (forceJsonGeneration) {
                    diagnostic("- force flag");
                }

                if (generateDueToBuildFileChange) {
                    diagnostic("- dependent build file missing or changed");
                }

                if (rebuildDueToMissingPreviousCommand) {
                    diagnostic("- missing previous command file %s", commandFile);
                }

                if (rebuildDueToChangeInCommandFile) {
                    diagnostic("- command changed from previous");
                }

                if (expectedJson.getParentFile().mkdirs()) {
                    diagnostic("created folder '%s'", expectedJson.getParentFile());
                }

                diagnostic("executing %s %s", getNativeBuildSystem().getName(), processBuilder);
                String buildOutput = ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                        androidBuilder,
                        processBuilder.createProcess());
                diagnostic("done executing %s", getNativeBuildSystem().getName());

                // Write the captured process output to a file for diagnostic purposes.
                File outputTextFile = new File(
                        ExternalNativeBuildTaskUtils.getOutputFolder(jsonFolder, abi),
                        String.format("%s_build_output.txt", getNativeBuildSystem().getName()));
                diagnostic("write build output output %s", outputTextFile.getAbsolutePath());
                Files.write(buildOutput, outputTextFile, Charsets.UTF_8);

                processBuildOutput(buildOutput, abi);

                if (!expectedJson.exists()) {
                    throw new GradleException(
                        String.format(
                            "Expected json generation to create '%s' but it didn't",
                            expectedJson));
                }

                // Write the ProcessInfo to a file, this has all the flags used to generate the
                // JSON. If any of these change later the JSON will be regenerated.

                diagnostic("write command file %s", commandFile.getAbsolutePath());
                Files.write(currentBuildCommand, commandFile, Charsets.UTF_8);
            } else {
                diagnostic("JSON '%s' was up-to-date", expectedJson);
            }
        }

        diagnostic("build complete");
    }

    /**
     * Derived class implements this method to post-process build output. Ndk-build uses this to
     * capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(@NonNull String buildOutput,
            @NonNull String abi) throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(
            @NonNull String abi, @NonNull File outputJson);

    /**
     * @return the native build system that is used to generate the JSON.
     */
    @NonNull
    public abstract NativeBuildSystem getNativeBuildSystem();

    /**
     * Log low level diagnostic information.
     */
    void diagnostic(String format, Object... args) {
        androidBuilder.getLogger().info(String.format(
                "External native build " + variantName + ": " + format, args));
    }

    /**
     * General configuration errors that apply to both CMake and ndk-build.
     */
    @NonNull
    List<String> getBaseConfigurationErrors() {
        List<String> messages = Lists.newArrayList();
        if (!getNdkFolder().isDirectory()) {
            messages.add(String.format(
                    "NDK not configured (%s).\n" +
                            "Download the NDK from http://developer.android.com/tools/sdk/ndk/." +
                            "Then add ndk.dir=path/to/ndk in local.properties.\n" +
                            "(On Windows, make sure you escape backslashes, "
                            + "e.g. C:\\\\ndk rather than C:\\ndk)", getNdkFolder()));
        }
        return messages;
    }

    @NonNull
    public Collection<NativeBuildConfigValue> readExistingNativeBuildConfigurations()
            throws IOException {
        List<NativeBuildConfigValue> result = Lists.newArrayList();
        List<File> existing = Lists.newArrayList();
        for(File file : getNativeBuildConfigurationsJsons()) {
            if (file.exists()) {
                existing.add(file);
            } else {
                // If the tool didn't create the JSON file then create fallback with the
                // information we have so the user can see partial information in the UI.
                NativeBuildConfigValue fallback = new NativeBuildConfigValue();
                fallback.buildFiles = Lists.newArrayList(makefile);
                result.add(fallback);
            }
        }

        result.addAll(ExternalNativeBuildTaskUtils.getNativeBuildConfigValues(
                existing,
                variantName));
        return result;
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull File projectDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File makefile,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        Preconditions.checkNotNull(sdkHandler.getNdkFolder());
        Preconditions.checkNotNull(sdkHandler.getSdkFolder());
        final BaseVariantData<? extends BaseVariantOutputData> variantData =
                scope.getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        File intermediates = FileUtils.join(
                scope.getGlobalScope().getIntermediatesDir(),
                buildSystem.getName(),
                variantData.getVariantConfiguration().getDirName());

        File soFolder = new File(intermediates, "lib");
        File objFolder = new File(intermediates, "obj");
        File jsonFolder = FileUtils.join(projectDir,
                "externalNativeBuild",
                buildSystem.getName(),
                variantData.getName());
        switch(buildSystem) {
            case NDK_BUILD: {
                CoreExternalNativeNdkBuildOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeNdkBuildOptions();
                return new NdkBuildExternalNativeJsonGenerator(
                        variantData.getName(),
                        ExternalNativeBuildTaskUtils.getAbiFilters(
                                options.getAbiFilters()),
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        jsonFolder,
                        makefile,
                        variantConfig.getBuildType().isDebuggable(),
                        options.getArguments(),
                        options.getcFlags(),
                        options.getCppFlags());
            }
            case CMAKE: {
                CoreExternalNativeCmakeOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeCmakeOptions();
                return new CmakeExternalNativeJsonGenerator(
                        sdkHandler.getSdkFolder(),
                        variantData.getName(),
                        ExternalNativeBuildTaskUtils.getAbiFilters(
                                options.getAbiFilters()),
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        jsonFolder,
                        makefile,
                        variantConfig.getBuildType().isDebuggable(),
                        options.getArguments(),
                        options.getcFlags(),
                        options.getCppFlags());
            }
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }

    @NonNull
    @SuppressWarnings("unused")
    @Input
    public File getMakefile() {
        return makefile;
    }

    @NonNull
    public File getObjFolder() {
        return objFolder;
    }

    @NonNull
    @SuppressWarnings("unused")
    @OutputDirectory
    public File getJsonFolder() {
        return jsonFolder;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Input
    public File getNdkFolder() {
        return ndkFolder;
    }

    @SuppressWarnings("unused")
    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Optional
    @Input
    public List<String> getBuildArguments() {
        return buildArguments;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Optional
    @Input
    public List<String> getcFlags() {
        return cFlags;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Optional
    @Input
    public List<String> getCppFlags() {
        return cppFlags;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Optional
    @Input
    public Set<String> getAbis() {
        return abis;
    }

    @NonNull
    @SuppressWarnings("unused")
    @OutputFiles
    public List<File> getNativeBuildConfigurationsJsons() {
        return nativeBuildConfigurationsJsons;
    }

    @NonNull
    public File getSoFolder() {
        return soFolder;
    }

    @SuppressWarnings("unused")
    @NonNull
    @Input
    public File getSdkFolder() {
        return sdkFolder;
    }
}
