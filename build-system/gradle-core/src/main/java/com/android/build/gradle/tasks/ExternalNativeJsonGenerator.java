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
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFiles;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for generation of native JSON.
 */
public abstract class ExternalNativeJsonGenerator {
    @NonNull
    private final NdkHandler ndkHandler;
    private final int minSdkVersion;
    @NonNull
    final String variantName;
    @NonNull
    private final List<Abi> abis;
    @NonNull
    private final AndroidBuilder androidBuilder;
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
            @NonNull NdkHandler ndkHandler,
            int minSdkVersion,
            @NonNull String variantName,
            @NonNull List<Abi> abis,
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
            @Nullable List<String> cppFlags,
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        this.ndkHandler = ndkHandler;
        this.minSdkVersion = minSdkVersion;
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
        this.nativeBuildConfigurationsJsons = nativeBuildConfigurationsJsons;
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
                    // If build file doesn't exist in JSON then the JSON should be regenerated to
                    // see if user has set a new one.
                    return true;
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
            diagnostic("building json with force flag %s", forceJsonGeneration);
            buildAndPropagateException(forceJsonGeneration);
        } catch (@NonNull IOException | GradleException e ) {
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

    private void buildAndPropagateException(boolean forceJsonGeneration)
            throws IOException, ProcessException {
        diagnostic("starting JSON generation");
        diagnostic("bringing JSONs up-to-date");

        Exception firstException = null;
        for (Abi abi : abis) {
            try {

                int abiPlatformVersion = ndkHandler.findSuitablePlatformVersion(
                        abi.getName(), minSdkVersion);
                diagnostic("using platform version %s for ABI %s and min SDK version %s",
                        abiPlatformVersion, abi, minSdkVersion);

                File expectedJson = ExternalNativeBuildTaskUtils.getOutputJson(
                        getJsonFolder(), abi.getName());

                ProcessInfoBuilder processBuilder = getProcessBuilder(abi.getName(),
                        abiPlatformVersion, expectedJson);

                // See whether the current build command matches a previously written build command.
                String currentBuildCommand = processBuilder.toString();
                boolean rebuildDueToMissingPreviousCommand = false;
                File commandFile = new File(
                        ExternalNativeBuildTaskUtils.getOutputFolder(jsonFolder, abi.getName()),
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
                    String buildOutput = ExternalNativeBuildTaskUtils
                            .executeBuildProcessAndLogError(
                                    androidBuilder,
                                    processBuilder.createProcess());
                    diagnostic("done executing %s", getNativeBuildSystem().getName());

                    // Write the captured process output to a file for diagnostic purposes.
                    File outputTextFile = new File(
                            ExternalNativeBuildTaskUtils.getOutputFolder(jsonFolder, abi.getName()),
                            String.format("%s_build_output.txt", getNativeBuildSystem().getName()));
                    diagnostic("write build output output %s", outputTextFile.getAbsolutePath());
                    Files.write(buildOutput, outputTextFile, Charsets.UTF_8);

                    processBuildOutput(buildOutput, abi.getName(), abiPlatformVersion);

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
            } catch (@NonNull GradleException | IOException | ProcessException e) {
                // If one ABI fails to build that doesn't mean others will. Continue processing
                // all ABIs so that we can get some JSON so the user can still edit the project
                // in Android Studio.
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        diagnostic("build complete");

        if (firstException == null) {
            diagnostic("build completed without problems");
            return;
        }

        diagnostic("build completed with problems");
        if (firstException instanceof GradleException) {
            throw (GradleException) firstException;
        }

        if (firstException instanceof IOException) {
            throw (IOException) firstException;
        }

        throw (ProcessException) firstException;
    }

    /**
     * Derived class implements this method to post-process build output. Ndk-build uses this to
     * capture and analyze the compile and link commands that were written to stdout.
     */
    abstract void processBuildOutput(@NonNull String buildOutput,
            @NonNull String abi, int abiPlatformVersion) throws IOException;

    @NonNull
    abstract ProcessInfoBuilder getProcessBuilder(
            @NonNull String abi, int abiPlatformVersion, @NonNull File outputJson);

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
        List<File> files = getNativeBuildConfigurationsJsons();
        diagnostic("reading %s JSON files", files.size());
        List<NativeBuildConfigValue> result = Lists.newArrayList();
        List<File> existing = Lists.newArrayList();
        for(File file : files) {
            if (file.exists()) {
                diagnostic("reading JSON file %s", file.getAbsolutePath());
                existing.add(file);
            } else {
                // If the tool didn't create the JSON file then create fallback with the
                // information we have so the user can see partial information in the UI.
                diagnostic("using fallback JSON for %s", file.getAbsolutePath());
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

    /**
     * Return all user-indicated ABIs. This may include misspelled ABIs. For that reason,
     * the result is Collection<String> instead of Collection<Abi>
     */
    @NonNull
    private static Collection<String> findUserRequestedAbis(
            @NonNull Collection<Abi> availableAbis,
            @Nullable Set<String> requestedAbis) {

        if (requestedAbis != null) {
            return requestedAbis;
        }

        // Find the names of available ABIs
        return availableAbis.stream()
                .map(Abi::getName)
                .collect(Collectors.toList());
    }

    /**
     * Check for user requested ABIs that aren't valid. Give a SyncIssue.
     */
    private static void checkForRequestedButUnknownAbis (
            @NonNull Collection<String> availableAbis,
            @NonNull Collection<String> userRequestedAbis,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull String variantName) {
        List<String> requestedButNotAvailable = Lists.newArrayList();
        for (String abiName : userRequestedAbis) {
            if (!availableAbis.contains(abiName)) {
                requestedButNotAvailable.add(abiName);
            }
        }

        if (!requestedButNotAvailable.isEmpty()) {
            androidBuilder.getErrorReporter().handleSyncError(
                    variantName,
                    SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    String.format("ABIs [%s] are not available for platform and will be "
                            + "excluded from building and packaging. Available ABIs are [%s].",
                            Joiner.on(", ").join(requestedButNotAvailable),
                            Joiner.on(", ").join(availableAbis)));
        }
    }

    /**
     * Return Abis that are available on the platform.
     */
    @NonNull
    private static List<Abi> filterToAvailableAbis(
            @NonNull Collection<String> availableAbis,
            @NonNull Collection<String> abiNames) {
        List<Abi> abis = Lists.newArrayList();
        for (String abiName : abiNames) {
            if (availableAbis.contains(abiName)) {
                abis.add(Abi.getByName(abiName));
            }
        }
        return abis;
    }

    /**
     * Get the set of abiFilters from the DSL.
     *
     * @return a Set of ABIs to build. If the set is empty then build nothing.
     */
    @NonNull
    private static Collection<String> getUserRequestedAbiFilters(
            @NonNull Collection<String> availableAbis,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull GradleVariantConfiguration variantConfig) {

        Set<String> externalNativeBuildAbiFilters = getExternalNativeBuildAbiFilters(buildSystem,
                variantConfig);

        // These are the abis from ndk.abiFilters that will be packaged. If they exist then we
        // don't need to build anything besides these (intersect with
        // externalNativeBuild.xxx.abiFilters)
        Set<String> ndkAbiFilters = variantConfig.getNdkConfig().getAbiFilters();
        if (ndkAbiFilters == null || ndkAbiFilters.isEmpty()) {
            // There was no ndk.abiFilters so use the build system specific abiFilters.
            return externalNativeBuildAbiFilters.isEmpty()
                    ? availableAbis
                    : externalNativeBuildAbiFilters;
        }

        // At this point, there are some ndk.abiFilters. If there are no build system specific
        // abi filters then just return ndk.abiFilters.
        if (externalNativeBuildAbiFilters.isEmpty()) {
            return ndkAbiFilters;
        }

        // At this point, there are both ndk.abiFilters and specific build system abiFilters.
        // We want to build the intersection of these. However, if the intersection is empty then
        // we don't want to build anything at all. This latter case will be indicated by returning
        // null.
        externalNativeBuildAbiFilters.retainAll(ndkAbiFilters);
        return externalNativeBuildAbiFilters;
    }

    /**
     * Get the set of abiFilters from the externalNativeBuild part of the DSL. For example,
     *
     * defaultConfig {
     *     cmake {
     *         abiFilters "x86", "x86_64"
     *     }
     * }
     *
     * @return a Set of ABIs to build. Return the empty set if nothing was specified.
     */
    @NonNull
    private static Set<String> getExternalNativeBuildAbiFilters(
            @NonNull NativeBuildSystem buildSystem,
            @NonNull GradleVariantConfiguration variantConfig) {
        switch(buildSystem) {
            case NDK_BUILD: {
                CoreExternalNativeNdkBuildOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeNdkBuildOptions();
                if (options != null) {
                    return checkNotNull(options.getAbiFilters());
                }
                break;
            }
            case CMAKE: {
                CoreExternalNativeCmakeOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeCmakeOptions();
                if (options != null) {
                    return checkNotNull(options.getAbiFilters());
                }
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
        return Sets.newHashSet();
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull File projectDir,
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File makefile,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        checkNotNull(sdkHandler.getSdkFolder());
        File ndkFolder =  sdkHandler.getNdkFolder();
        if (ndkFolder == null || !ndkFolder.isDirectory()) {
            throw new InvalidUserDataException(String.format(
                    "NDK not configured. %s\n" +
                            "Download it with SDK manager.)",
                    ndkFolder== null ? "" : ndkFolder));
        }
        final BaseVariantData<? extends BaseVariantOutputData> variantData =
                scope.getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        File intermediates = FileUtils.join(
                scope.getGlobalScope().getIntermediatesDir(),
                buildSystem.getName(),
                variantData.getVariantConfiguration().getDirName());

        File soFolder = new File(intermediates, "lib");
        File externalNativeBuildFolder = FileUtils.join(projectDir,
                ".externalNativeBuild",
                buildSystem.getName(),
                variantData.getName());
        File objFolder = new File(externalNativeBuildFolder, "obj");

        // Get the highest platform version below compileSdkVersion
        NdkHandler ndkHandler = scope.getGlobalScope().getNdkHandler();

        ApiVersion minSdkVersion = scope
                .getVariantData()
                .getVariantConfiguration()
                .getMergedFlavor()
                .getMinSdkVersion();
        int minSdkVersionApiLevel = minSdkVersion == null ? 1 : minSdkVersion.getApiLevel();

        // Get the set of ABIs that are available on this platform
        Collection<String> availableAbis = ndkHandler.getSupportedAbis().stream()
                .map(Abi::getName)
                .collect(Collectors.toList());

        // Get the filters specified in the DSL. Will be null if we should build all known ABIs.
        Collection<String> userRequestedAbis = getUserRequestedAbiFilters(availableAbis,
                buildSystem, variantConfig);

        // These are ABIs that are available on the current platform
        List<Abi> validAbis = filterToAvailableAbis(availableAbis, userRequestedAbis);

        // If the user requested ABIs that aren't valid for the current platform then give
        // them a SyncIssue that describes which ones are the problem.
        checkForRequestedButUnknownAbis(availableAbis, userRequestedAbis, androidBuilder,
                variantData.getName());

        // Produce the list of expected JSON files. This list includes possibly invalid ABIs
        // so that generator can create fallback JSON for them.
        List<File> expectedJsons = ExternalNativeBuildTaskUtils.getOutputJsons(
                externalNativeBuildFolder, userRequestedAbis);

        switch(buildSystem) {
            case NDK_BUILD: {
                CoreExternalNativeNdkBuildOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeNdkBuildOptions();
                checkNotNull(options);
                return new NdkBuildExternalNativeJsonGenerator(
                        ndkHandler,
                        minSdkVersionApiLevel,
                        variantData.getName(),
                        validAbis,
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        externalNativeBuildFolder,
                        makefile,
                        variantConfig.getBuildType().isDebuggable(),
                        options.getArguments(),
                        options.getcFlags(),
                        options.getCppFlags(),
                        expectedJsons);
            }
            case CMAKE: {
                CoreExternalNativeCmakeOptions options =
                        variantConfig.getExternalNativeBuildOptions()
                                .getExternalNativeCmakeOptions();
                checkNotNull(options);
                return new CmakeExternalNativeJsonGenerator(
                        sdkHandler.getSdkFolder(),
                        ndkHandler,
                        minSdkVersionApiLevel,
                        variantData.getName(),
                        validAbis,
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        externalNativeBuildFolder,
                        makefile,
                        variantConfig.getBuildType().isDebuggable(),
                        options.getArguments(),
                        options.getcFlags(),
                        options.getCppFlags(),
                        expectedJsons);
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

    @NonNull
    List<Abi> getAbis() {
        return abis;
    }
}
