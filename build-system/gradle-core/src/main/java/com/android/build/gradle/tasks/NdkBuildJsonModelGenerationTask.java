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
import com.android.build.gradle.external.gnumake.NativeBuildConfigValueBuilder;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.PlainFileGsonTypeAdaptor;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.CoreNdkBuildOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * This is a Gradle Task that invokes an external ndk-build build file.
 * It works by invoking ndk-build with -n flag which emits all commands that would be executed
 * by the build. This list of commands is parsed (with platform-specific parsing) to understand
 * the flow from .c, .cpp file through linker to final .so file.
 */
@ParallelizableTask
public class NdkBuildJsonModelGenerationTask extends ExternalNativeBuildJsonModelGenerationBaseTask {

    @Override
    void createNativeBuildJson(@NonNull String abi, @NonNull File outputJson)
            throws ProcessException, IOException {
        checkConfiguration();

        ProcessInfoBuilder builder = getProcessBuilder(abi);

        // Invoke ndk-build -n and capture resulting info-level logger output to be parsed.
        diagnostic("executing ndk-build %s\n", builder);
        final StringBuilder sb = new StringBuilder();
        final StringBuilder all = new StringBuilder();
        final ILogger logger = getBuilder().getLogger();
        getBuilder().executeProcess(builder.createProcess(),
                new LoggedProcessOutputHandler(new ILogger() {
            @Override
            public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
                all.append(String.format(msgFormat, args));
                logger.error(t, msgFormat, args);
            }

            @Override
            public void warning(@NonNull String msgFormat, Object... args) {
                all.append(String.format(msgFormat, args));
                logger.warning(msgFormat, args);
            }

            @Override
            public void info(@NonNull String msgFormat, Object... args) {
                all.append(String.format(msgFormat, args));
                sb.append(String.format(msgFormat, args));
                logger.info(msgFormat, args);
            }

            @Override
            public void verbose(@NonNull String msgFormat, Object... args) {
                all.append(String.format(msgFormat, args));
                logger.verbose(msgFormat, args);
            }
        })).rethrowFailure().assertNormalExitValue();

        // Write the captured ndk-build output to a file for diagostic purposes.
        diagnostic("write the raw output from ndk-build to a file");
        File outputTextFile = new File(getJsonFolder(), "ndk-build-output.txt");
        Files.write(sb.toString(), outputTextFile, Charsets.UTF_8);

        // Write the captured ndk-build output to a file for diagostic purposes.
        diagnostic("parse and convert ndk-build output to build configuration JSON");
        NativeBuildConfigValue buildConfig = new NativeBuildConfigValueBuilder(getProjectPath())
                .addCommands(
                    getBuildCommand(abi),
                    getVariantName(),
                    sb.toString(),
                    isWin32())
                .build();

        String actualResult = new GsonBuilder()
                .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                .setPrettyPrinting()
                .create()
                .toJson(buildConfig);

        // Write the captured ndk-build output to a file for diagostic purposes.
        Files.write(actualResult, outputJson, Charsets.UTF_8);
    }

    /**
     * This is a Gradle Task that invokes an external CMake build file.
     */
    private boolean isWin32() {
        return (CURRENT_PLATFORM == PLATFORM_WINDOWS);
    }

    /**
     * Get the base list of arguments for invoking ndk-build.
     */
    private List<String> getBaseArgs(String abi) {
        List<String> result = Lists.newArrayList();
        if (getProjectPath() == null) {
            result.add("NDK_PROJECT_PATH=null");
            result.add("APP_BUILD_SCRIPT=null");
        } else {
            result.add("NDK_PROJECT_PATH=" + getProjectPath());
            result.add("APP_BUILD_SCRIPT=" + getProjectPath());
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

        IAndroidTarget target = getBuilder().getTarget();
        if (!target.isPlatform()) {
            target = target.getParent();
        }
        result.add("APP_PLATFORM=" + target.hashString());

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
     * Get the path of the ndk-build script
     */
    private String getNdkBuild() {
        String tool = "ndk-build";
        if (isWin32()) {
            tool += ".cmd";
        }
        return new File(getNdkFolder(), tool).getAbsolutePath();
    }

    /**
     * Get the build command
     */
    private String getBuildCommand(String abi) {
        return getNdkBuild() + " " + Joiner.on(" ").join(getBaseArgs(abi));
    }

    /**
     * Get the process builder with -n flag. This will tell ndk-build to emit the steps that it
     * would do to execute the build.
     */
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
        if (getNdkFolder() == null || !getNdkFolder().isDirectory()) {
            throw new GradleException(
                    "NDK not configured.\n" +
                            "Download the NDK from http://developer.android.com/tools/sdk/ndk/." +
                            "Then add ndk.dir=path/to/ndk in local.properties.\n" +
                            "(On Windows, make sure you escape backslashes, e.g. C:\\\\ndk rather than C:\\ndk)");
        }

        if (getProjectPath() == null) {
            throw new GradleException(
                    "ndkBuild.path must be set to a valid Android.mk style file");
        }

        if (!getProjectPath().exists()) {
            throw new GradleException(
                    String.format("ndk-build project '%s' does not exist", getProjectPath()));
        }
    }

    public static class ConfigAction extends ConfigActionBase
            implements TaskConfigAction<NdkBuildJsonModelGenerationTask>  {

        private final CoreNdkBuildOptions options;

        public ConfigAction(
                @NonNull File projectPath,
                @NonNull CoreNdkBuildOptions options,
                @NonNull AndroidBuilder androidBuilder,
                @NonNull SdkHandler sdkHandler,
                @NonNull VariantScope scope) {
            super(projectPath, sdkHandler, androidBuilder, scope);
            this.options = options;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("NdkBuildJsonModelGenerationTask");
        }

        @NonNull
        @Override
        public Class<NdkBuildJsonModelGenerationTask> getType() {
            return NdkBuildJsonModelGenerationTask.class;
        }

        @Override
        public void execute(@NonNull NdkBuildJsonModelGenerationTask task) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            task.setcFlags(options.getcFlags());
            task.addcFlags(variantConfig.getExternalNativeNdkBuildOptions().getcFlags());
            task.setCppFlags(options.getCppFlags());
            task.addCppFlags(variantConfig.getExternalNativeNdkBuildOptions().getCppFlags());
            task.addAbis(variantConfig.getExternalNativeNdkBuildOptions().getAbiFilters());
            initCommon(task, scope);
        }
    }
}
