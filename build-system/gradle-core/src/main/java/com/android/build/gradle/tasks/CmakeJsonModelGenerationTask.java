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
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.model.CoreCmakeOptions;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.IAndroidTarget;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.ParallelizableTask;

import java.io.File;

/**
 * This is a Gradle Task that invokes an external CMake build file to create standard JSON model.
 */
@ParallelizableTask
public class CmakeJsonModelGenerationTask extends ExternalNativeBuildJsonModelGenerationBaseTask {

    // Cmake requires the android SDK folder so that it can locate the CMake tool.
    // This CMake is customized to support emitting a JSON compilation database.
    private File sdkDirectory;

    @Input
    public File getSdkDirectory() {
        return sdkDirectory;
    }

    public void setSdkFolder(File sdkDirectory) {
        this.sdkDirectory = sdkDirectory;
    }


    @Override
    void createNativeBuildJson(@NonNull String abi, @NonNull File outputJson) throws ProcessException {
        checkConfiguration();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        File executable = new File(getSdkDirectory(), "cmake/bin/cmake");
        if (!executable.exists()) {
            throw new RuntimeException(
                    String.format("Android SDK cmake did not exist at %s", executable));
        }

        builder.setExecutable(executable);
        builder.addArgs(String.format("-H%s", getProjectPath()));
        builder.addArgs(String.format("-B%s", outputJson.getParentFile()));
        builder.addArgs("-GAndroid Gradle - Ninja");
        builder.addArgs(String.format("-DANDROID_ABI=%s", abi));
        builder.addArgs(String.format("-DANDROID_NDK=%s", getNdkFolder()));
        builder.addArgs(
                String.format("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=%s", new File(getSoFolder(), abi)));
        builder.addArgs(
                String.format("-DCMAKE_BUILD_TYPE=%s", isDebuggable() ? "Debug" : "Release"));
        builder.addArgs(String.format("-DCMAKE_MAKE_PROGRAM=%s/cmake/bin/ninja",
                getSdkDirectory().getAbsolutePath()));
        builder.addArgs(
                String.format("-DCMAKE_TOOLCHAIN_FILE=%s/cmake/android.toolchain.cmake",
                        getSdkDirectory().getAbsolutePath()));

        IAndroidTarget target = getBuilder().getTarget();
        if (!target.isPlatform()) {
            target = target.getParent();
        }
        builder.addArgs(
                String.format("-DANDROID_NATIVE_API_LEVEL=%s", target.hashString()));

        ProcessOutputHandler handler = new LoggedProcessOutputHandler(
                getBuilder().getLogger());

        if (abi == null) {
            throw new GradleException("expected abi to present in cmake task");
        }

        getBuilder().executeProcess(builder.createProcess(), handler)
                .rethrowFailure().assertNormalExitValue();
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

        if (getProjectPath() == null || !getProjectPath().exists()) {
            throw new GradleException(
                    "cmake.path must be set to a valid CMake project folder");
        }
    }

    public static class ConfigAction extends ConfigActionBase implements TaskConfigAction<CmakeJsonModelGenerationTask> {

        private final CoreCmakeOptions options;

        public ConfigAction(
                @NonNull File projectPath,
                @NonNull CoreCmakeOptions options,
                @NonNull AndroidBuilder androidBuilder,
                @NonNull SdkHandler sdkHandler,
                @NonNull VariantScope scope) {
            super(projectPath, sdkHandler, androidBuilder, scope);
            this.options = options;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("CmakeJsonModelGenerationTask");
        }

        @NonNull
        @Override
        public Class<CmakeJsonModelGenerationTask> getType() {
            return CmakeJsonModelGenerationTask.class;
        }

        @Override
        public void execute(@NonNull CmakeJsonModelGenerationTask task) {
            final BaseVariantData<? extends BaseVariantOutputData> variantData = scope.getVariantData();
            final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
            task.setSdkFolder(sdkHandler.getSdkFolder());
            task.setcFlags(options.getcFlags());
            task.setCppFlags(options.getCppFlags());
            task.addCppFlags(variantConfig.getExternalNativeCmakeOptions().getCppFlags());
            task.addAbis(variantConfig.getExternalNativeCmakeOptions().getAbiFilters());
            initCommon(task, scope);
        }
    }
}
