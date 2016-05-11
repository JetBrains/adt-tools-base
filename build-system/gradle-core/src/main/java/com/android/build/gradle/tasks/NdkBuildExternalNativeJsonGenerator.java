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
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * ndk-build JSON generation logic. This is separated from the corresponding ndk-build task so that
 * JSON can be generated during configuration.
 */
class NdkBuildExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {

    NdkBuildExternalNativeJsonGenerator(
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
        super(variantName, abis, androidBuilder, sdkFolder, ndkFolder, soFolder, objFolder,
                jsonFolder, makeFileOrFolder, debuggable, cFlags, cppFlags);
    }

    @Override
    void createNativeBuildJson(
            @NonNull String abi,
            @NonNull File outputJson) throws ProcessException, IOException {
        checkConfiguration();

        ProcessInfoBuilder builder = getProcessBuilder(abi);

        // Invoke ndk-build -n and capture resulting info-level logger output to be parsed.
        diagnostic("executing ndk-build %s\n", builder);
        String info = ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                androidBuilder,
                builder.createProcess());
        
        // Write the captured ndk-build output to a file for diagnostic purposes.
        diagnostic("write the raw output from ndk-build to a file");
        File outputTextFile = new File(getJsonFolder(), "ndk-build-output.txt");
        Files.write(info, outputTextFile, Charsets.UTF_8);

        // Write the captured ndk-build output to a file for diagnostic purposes.
        diagnostic("parse and convert ndk-build output to build configuration JSON");
        NativeBuildConfigValue buildConfig = new NativeBuildConfigValueBuilder(getMakeFile())
                .addCommands(
                        getBuildCommand(abi),
                        variantName,
                        info,
                        isWindows())
                .build();

        String actualResult = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .setPrettyPrinting()
                .create()
                .toJson(buildConfig);

        // Write the captured ndk-build output to a file for diagnostic purposes.
        Files.write(actualResult, outputJson, Charsets.UTF_8);
    }

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
        if (getMakeFileOrFolder().isDirectory()) {
            return new File(getMakeFileOrFolder(), "Android.mk");
        }
        return getMakeFileOrFolder();
    }

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
    @NonNull
    private ProcessInfoBuilder getProcessBuilder(String abi) {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(getNdkBuild())
                .addArgs(getBaseArgs(abi))
                .addArgs("-n");
        return builder;
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
    private List<String> getBaseArgs(String abi) {
        List<String> result = Lists.newArrayList();
        result.add("NDK_PROJECT_PATH=" + getMakeFile());
        result.add("APP_BUILD_SCRIPT=" + getMakeFile());

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

        IAndroidTarget target = androidBuilder.getTarget();
        if (target != null && !target.isPlatform()) {
            target = target.getParent();
        }

        if (target != null) {
            result.add("APP_PLATFORM=" + target.hashString());
        }

        result.add("NDK_OUT=" + getObjFolder().getAbsolutePath());
        result.add("NDK_LIBS_OUT=" + getSoFolder().getAbsolutePath());

        if (!Strings.isNullOrEmpty(getcFlags())) {
            result.add(String.format("APP_CFLAGS+=\"%s\"", getcFlags()));
        }

        if (!Strings.isNullOrEmpty(getCppFlags())) {
            result.add(String.format("APP_CPPFLAGS+=\"%s\"", getCppFlags()));
        }

        return result;
    }

    /**
     * Get the build command
     */
    @NonNull
    private String getBuildCommand(String abi) {
        return getNdkBuild() + " " + Joiner.on(" ").join(getBaseArgs(abi));
    }

    /**
     * Construct list of errors that can be known at configuration time.
     */
    @NonNull
    private List<String> getConfigurationErrors() {
        List<String> messages = Lists.newArrayList();
        if (getMakeFileOrFolder().isDirectory()) {
            if (!getMakeFile().isFile()) {
                messages.add(
                        String.format("Gradle project ndkBuild.path folder is %s but "
                                + "there is no file named %s there",
                                getMakeFileOrFolder(),
                                getMakeFile().getName()));
            }
        } else if (!getMakeFileOrFolder().exists()) {
            messages.add(
                    String.format("Gradle project ndkBuild.path is %s but that file doesn't exist",
                            getMakeFileOrFolder()));
        }

        messages.addAll(getBaseConfigurationErrors());
        return messages;
    }
}
