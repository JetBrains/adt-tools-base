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
import com.android.build.gradle.external.gnumake.NativeBuildConfigValueBuilder;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * ndk-build JSON generation logic. This is separated from the corresponding ndk-build task so that
 * JSON can be generated during configuration.
 */
class NdkBuildExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {

    NdkBuildExternalNativeJsonGenerator(
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
            @NonNull File makeFile,
            boolean debuggable,
            @Nullable List<String> buildArguments,
            @Nullable List<String> cFlags,
            @Nullable List<String> cppFlags,
            @NonNull List<File> nativeBuildConfigurationsJsons) {
        super(ndkHandler,
                minSdkVersion,
                variantName,
                abis,
                androidBuilder,
                sdkFolder,
                ndkFolder,
                soFolder,
                new File(objFolder, "local"),  // ndk-build create libraries in a "local" subfolder.
                jsonFolder,
                makeFile,
                debuggable,
                buildArguments,
                cFlags,
                cppFlags,
                nativeBuildConfigurationsJsons);
    }

    @Override
    void processBuildOutput(@NonNull String buildOutput, @NonNull String abi,
            int abiPlatformVersion) throws IOException {
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        File applicationMk = new File(getMakeFile().getParent(), "Application.mk");

        // Write the captured ndk-build output to a file for diagnostic purposes.
        diagnostic("parse and convert ndk-build output to build configuration JSON");
        NativeBuildConfigValue buildConfig = new NativeBuildConfigValueBuilder(getMakeFile())
                .addCommands(
                        getBuildCommand(abi, abiPlatformVersion, applicationMk),
                        variantName,
                        buildOutput,
                        isWindows())
                .build();

        if (applicationMk.exists()) {
            diagnostic("found application make file %s", applicationMk.getAbsolutePath());
            Preconditions.checkNotNull(buildConfig.buildFiles);
            buildConfig.buildFiles.add(applicationMk);
        }

        String actualResult = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .setPrettyPrinting()
                .create()
                .toJson(buildConfig);

        // Write the captured ndk-build output to JSON file
        File expectedJson = ExternalNativeBuildTaskUtils.getOutputJson(getJsonFolder(), abi);
        Files.write(actualResult, expectedJson, Charsets.UTF_8);
    }

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
    @NonNull
    @Override
    ProcessInfoBuilder getProcessBuilder(@NonNull String abi, int abiPlatformVersion,
            @NonNull File outputJson) {
        checkConfiguration();
        // Discover Application.mk if one exists next to Android.mk
        // If there is an Application.mk file next to Android.mk then pick it up.
        File applicationMk = new File(getMakeFile().getParent(), "Application.mk");
        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(getNdkBuild())
                .addArgs(getBaseArgs(abi, abiPlatformVersion, applicationMk))
                // Disable response files so we can parse the command line.
                .addArgs("APP_SHORT_COMMANDS=false")
                .addArgs("LOCAL_SHORT_COMMANDS=false")
                .addArgs("-n");
        return builder;
    }

    @NonNull
    @Override
    public NativeBuildSystem getNativeBuildSystem() {
        return NativeBuildSystem.NDK_BUILD;
    }

    /**
     * Get the path of the ndk-build script.
     */
    private String getNdkBuild() {
        String tool = "ndk-build";
        if (isWindows()) {
            tool += ".cmd";
        }
        return new File(getNdkFolder(), tool).getAbsolutePath();
    }

    /**
     * If the make file is a directory then get the implied file, otherwise return the path.
     */
    @NonNull
    private File getMakeFile() {
        if (getMakefile().isDirectory()) {
            return new File(getMakefile(), "Android.mk");
        }
        return getMakefile();
    }

    /**
     * Check whether the configuration looks good enough to generate JSON files and expect that
     * the result will be valid.
     */
    private void checkConfiguration() {
        List<String> configurationErrors = getConfigurationErrors();
        if (!configurationErrors.isEmpty()) {
            throw new GradleException(Joiner.on("\n").join(configurationErrors));
        }
    }

    /**
     * Get the base list of arguments for invoking ndk-build.
     */
    @NonNull
    private List<String> getBaseArgs(@NonNull String abi, int abiPlatformVersion,
            @NonNull File applicationMk) {
        List<String> result = Lists.newArrayList();
        result.add("NDK_PROJECT_PATH=null");
        result.add("APP_BUILD_SCRIPT=" + getMakeFile());

        if (applicationMk.exists()) {
            // NDK_APPLICATION_MK specifies the Application.mk file.
            result.add("NDK_APPLICATION_MK=" + applicationMk.getAbsolutePath());
        }

        // APP_ABI and NDK_ALL_ABIS work together. APP_ABI is the specific ABI for this build.
        // NDK_ALL_ABIS is the universe of all ABIs for this build. NDK_ALL_ABIS is set to just the
        // current ABI. If we don't do this, then ndk-build will erase build artifacts for all abis
        // aside from the current.
        result.add("APP_ABI=" + abi);
        result.add("NDK_ALL_ABIS=" + abi);

        if (isDebuggable()) {
            result.add("NDEBUG=1");
        } else {
            result.add("NDEBUG=0");
        }

        result.add("APP_PLATFORM=android-" + abiPlatformVersion);
        // getObjFolder is set to the "local" subfolder in the user specified directory, therefore,
        // NDK_OUT should be set to getObjFolder().getParent() instead of getObjFolder().
        result.add("NDK_OUT=" + getObjFolder().getParent());
        result.add("NDK_LIBS_OUT=" + getSoFolder().getAbsolutePath());

        for (String flag : getcFlags()) {
            result.add(String.format("APP_CFLAGS+=\"%s\"", flag));
        }

        for (String flag : getCppFlags()) {
            result.add(String.format("APP_CPPFLAGS+=\"%s\"", flag));
        }

        for (String argument : getBuildArguments()) {
            result.add(argument);
        }

        return result;
    }

    /**
     * Get the build command
     */
    @NonNull
    private String getBuildCommand(@NonNull String abi, int abiPlatformVersion,
            @NonNull File applicationMk) {
        return getNdkBuild() + " " + Joiner.on(" ").join(getBaseArgs(abi, abiPlatformVersion,
                applicationMk));
    }

    /**
     * Construct list of errors that can be known at configuration time.
     */
    @NonNull
    private List<String> getConfigurationErrors() {
        List<String> messages = Lists.newArrayList();
        if (getMakefile().isDirectory()) {
            messages.add(
                    String.format("Gradle project ndkBuild.path %s is a folder. "
                            + "Only files (like Android.mk) are allowed.",
                            getMakefile()));
        } else if (!getMakefile().exists()) {
            messages.add(
                    String.format("Gradle project ndkBuild.path is %s but that file doesn't exist",
                            getMakefile()));
        }

        messages.addAll(getBaseConfigurationErrors());
        return messages;
    }
}
