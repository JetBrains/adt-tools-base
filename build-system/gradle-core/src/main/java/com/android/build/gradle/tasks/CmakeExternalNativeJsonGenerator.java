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
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.gradle.api.GradleException;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * CMake JSON generation logic. This is separated from the corresponding CMake task so that
 * JSON can be generated during configuration.
 */
class CmakeExternalNativeJsonGenerator extends ExternalNativeJsonGenerator {

    CmakeExternalNativeJsonGenerator(
            @Nullable File sdkDirectory,
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
        Preconditions.checkNotNull(sdkDirectory);
    }

    @Override
    void createNativeBuildJson(
            @NonNull String abi,
            @NonNull File outputJson) throws ProcessException {
        checkConfiguration();

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        File makeFile = getMakeFileOrFolder();
        if (makeFile.isFile()) {
            // If a file, trim the CMakeLists.txt so that we have the folder.
            makeFile = makeFile.getParentFile();
        }

        builder.setExecutable(getCmakeExecutable());
        builder.addArgs(String.format("-H%s", makeFile));
        builder.addArgs(String.format("-B%s", outputJson.getParentFile()));
        builder.addArgs("-GAndroid Gradle - Ninja");
        builder.addArgs(String.format("-DANDROID_ABI=%s", abi));
        builder.addArgs(String.format("-DANDROID_NDK=%s", getNdkFolder()));
        builder.addArgs(
                String.format("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=%s",
                        new File(getObjFolder(), abi)));
        builder.addArgs(
                String.format("-DCMAKE_BUILD_TYPE=%s", isDebuggable() ? "Debug" : "Release"));
        builder.addArgs(String.format("-DCMAKE_MAKE_PROGRAM=%s",
                getNinjaExecutable().getAbsolutePath()));
        builder.addArgs(String.format("-DCMAKE_TOOLCHAIN_FILE=%s",
                getToolChainFile().getAbsolutePath()));

        IAndroidTarget target = androidBuilder.getTarget();
        if (target != null && !target.isPlatform()) {
            target = target.getParent();
        }

        if (target != null) {
            builder.addArgs(
                    String.format("-DANDROID_NATIVE_API_LEVEL=%s", target.hashString()));
        }

        if (!Strings.isNullOrEmpty(getcFlags())) {
            builder.addArgs(String.format("-DCMAKE_C_FLAGS=%s", getcFlags()));
        }

        if (!Strings.isNullOrEmpty(getCppFlags())) {
            builder.addArgs(String.format("-DCMAKE_CXX_FLAGS=%s", getCppFlags()));
        }

        diagnostic("executing CMake %s", builder);

        ExternalNativeBuildTaskUtils.executeBuildProcessAndLogError(
                androidBuilder,
                builder.createProcess());

        diagnostic("done executing CMake");
    }

    @NonNull
    private File getToolChainFile() {
        return new File(getCmakeFolder(), "android.toolchain.cmake");
    }


    @NonNull
    private File getCmakeFolder() {
        return new File(getSdkFolder(), "cmake");
    }

    @NonNull
    private File getCmakeBinFolder() {
        return new File(getCmakeFolder(), "bin");
    }

    @NonNull
    private File getCmakeExecutable() {
        if (isWindows()) {
            return new File(getCmakeBinFolder(), "cmake.exe");
        }
        return new File(getCmakeBinFolder(), "cmake");
    }

    @NonNull
    private File getNinjaExecutable() {
        if (isWindows()) {
            return new File(getCmakeBinFolder(), "ninja.exe");
        }
        return new File(getCmakeBinFolder(), "ninja");
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
     * Construct list of errors that can be known at configuration time.
     */
    @NonNull
    private List<String> getConfigurationErrors() {
        List<String> messages = Lists.newArrayList();

        String cmakeListsTxt = "CMakeLists.txt";
        if (getMakeFileOrFolder().isDirectory()) {
            File cmakeLists = new File(getMakeFileOrFolder(), cmakeListsTxt);
            if (!cmakeLists.exists()) {
                messages.add(String.format(
                        "Gradle project cmake.path specifies %s but there is"
                                + " no %s there",
                        getMakeFileOrFolder(),
                        cmakeListsTxt));
            }
        } else if (getMakeFileOrFolder().isFile()) {
            String filename = getMakeFileOrFolder().getName();
            if (!filename.equals(cmakeListsTxt)) {
                messages.add(String.format(
                        "Gradle project cmake.path specifies %s but there is"
                                + " it must be %s",
                        filename,
                        cmakeListsTxt));
            }
        } else {
            messages.add(
                    String.format(
                            "Gradle project cmake.path is %s but that folder or file doesn't exist",
                            getMakeFileOrFolder()));
        }

        File cmakeExecutable = getCmakeExecutable();
        if (!cmakeExecutable.exists()) {
            messages.add(
                    String.format("Failed to find CMake.\n"
                                    + "Install from Android Studio under File/Settings/"
                                    + "Appearance & Behavior/System Settings/Android SDK/SDK Tools/CMake.\n"
                                    + "Expected CMake executable at %s.",
                            cmakeExecutable));
        }
        messages.addAll(getBaseConfigurationErrors());
        return messages;
    }

}
