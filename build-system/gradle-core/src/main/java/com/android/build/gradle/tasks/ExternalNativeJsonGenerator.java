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
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

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
    private final File makeFileOrFolder;
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
    private final String cFlags;
    @NonNull
    private final String cppFlags;
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
            @NonNull File makeFileOrFolder,
            boolean debuggable,
            @Nullable String cFlags,
            @Nullable String cppFlags) {
        Preconditions.checkArgument(!abis.isEmpty(), "No abis specified");
        this.variantName = variantName;
        this.abis = abis;
        this.androidBuilder = androidBuilder;
        this.sdkFolder = sdkFolder;
        this.ndkFolder = ndkFolder;
        this.soFolder = soFolder;
        this.objFolder = objFolder;
        this.jsonFolder = jsonFolder;
        this.makeFileOrFolder = makeFileOrFolder;
        this.debuggable = debuggable;
        this.cFlags = cFlags == null ? "" : cFlags;
        this.cppFlags = cppFlags == null ? "" : cppFlags;
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

    public void build() throws IOException, ProcessException {
        build(false);
    }

    public void build(boolean forceJsonGeneration) throws IOException, ProcessException {
        diagnostic("starting build");
        diagnostic("bringing JSONs up-to-date");
        for (String abi : abis) {
            File expectedJson = ExternalNativeBuildTaskUtils
                .getOutputJson(getJsonFolder(), abi);
            if (forceJsonGeneration || ExternalNativeBuildTaskUtils
                    .shouldRebuildJson(expectedJson, variantName)) {
                if (forceJsonGeneration) {
                    diagnostic("force rebuilding json '%s'", expectedJson);

                } else {
                    diagnostic("rebuilding json '%s'", expectedJson);
                }
                if (expectedJson.getParentFile().mkdirs()) {
                    diagnostic("created folder '%s'", expectedJson.getParentFile());
                }

                createNativeBuildJson(abi, expectedJson);

                if (!expectedJson.exists()) {
                    throw new GradleException(
                        String.format(
                            "Expected json generation to create '%s' but it didn't",
                            expectedJson));
                }
            } else {
                diagnostic("json '%s' was up-to-date", expectedJson);
            }
        }
        diagnostic("build complete");
    }

    /**
     * Derived class implements this method to produce JSON model output file
     *
     * @param abi        -- the abi to produce JSON for.
     * @param outputJson -- the file to write the JSON to.
     */
    abstract void createNativeBuildJson(@NonNull String abi, @NonNull File outputJson)
            throws ProcessException, IOException;

    /**
     * @return the native build system that is used to generate the JSON.
     */
    public abstract NativeBuildSystem getNativeBuildSystem();

    /**
     * Log low level diagnostic information.
     */
    void diagnostic(String format, Object... args) {
        androidBuilder.getLogger().info(String.format(
                "External native build generation " + variantName +
                        ": " + format + "\n", args));
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
        List<File> existing = Lists.newArrayList();
        for(File file : getNativeBuildConfigurationsJsons()) {
            if (file.exists()) {
                existing.add(file);
            }
        }

        return ExternalNativeBuildTaskUtils.getNativeBuildConfigValues(
                existing,
                variantName);
    }

    @NonNull
    public static ExternalNativeJsonGenerator create(
            @NonNull NativeBuildSystem buildSystem,
            @NonNull File projectPath,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantScope scope) {
        Preconditions.checkNotNull(sdkHandler.getNdkFolder());
        Preconditions.checkNotNull(sdkHandler.getSdkFolder());
        final BaseVariantData<? extends BaseVariantOutputData> variantData =
                scope.getVariantData();
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final Set<String> abis = ExternalNativeBuildTaskUtils.getAbiFilters(
                        variantConfig.getExternalNativeCmakeOptions().getAbiFilters());
        File intermediates = FileUtils.join(
                scope.getGlobalScope().getIntermediatesDir(),
                buildSystem.getName(),
                variantData.getVariantConfiguration().getDirName());

        File soFolder = new File(intermediates, "lib");
        File objFolder = new File(intermediates, "obj");
        File jsonFolder = new File(intermediates, "json");

        switch(buildSystem) {
            case NDK_BUILD: {
                return new NdkBuildExternalNativeJsonGenerator(
                        variantData.getName(),
                        abis,
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        jsonFolder,
                        projectPath,
                        variantConfig.getBuildType().isDebuggable(),
                        variantConfig.getExternalNativeNdkBuildOptions().getcFlags(),
                        variantConfig.getExternalNativeNdkBuildOptions().getCppFlags());
            }
            case CMAKE: {
                return new CmakeExternalNativeJsonGenerator(
                        sdkHandler.getSdkFolder(),
                        variantData.getName(),
                        abis,
                        androidBuilder,
                        sdkHandler.getSdkFolder(),
                        sdkHandler.getNdkFolder(),
                        soFolder,
                        objFolder,
                        jsonFolder,
                        projectPath,
                        variantConfig.getBuildType().isDebuggable(),
                        variantConfig.getExternalNativeCmakeOptions().getcFlags(),
                        variantConfig.getExternalNativeCmakeOptions().getCppFlags());

            }
            default:
                throw new IllegalArgumentException("Unknown ExternalNativeJsonGenerator type");
        }
    }

    @NonNull
    @SuppressWarnings("unused")
    @Input
    public File getMakeFileOrFolder() {
        return makeFileOrFolder;
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
    public String getcFlags() {
        return cFlags;
    }

    @NonNull
    @SuppressWarnings("unused")
    @Optional
    @Input
    public String getCppFlags() {
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
