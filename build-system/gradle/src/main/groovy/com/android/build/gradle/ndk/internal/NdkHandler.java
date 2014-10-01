/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.ndk.internal;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.build.gradle.ndk.NdkExtension;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.nativebinaries.platform.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Handles NDK related information.
 */
public class NdkHandler {

    // Map of ABI to toolchain platform string.
    private static final Map<String, String> PLATFORM_STRING;

    // Map of ABI to target architecture
    private static final Map<String, String> ARCHITECTURE_STRING;

    // Map of toolchain names to the subdirectory name containing the toolchain.
    private static final Map<String, String> TOOLCHAIN_STRING;

    private static final List<String> ABI32 = ImmutableList.of(
            SdkConstants.ABI_INTEL_ATOM,
            SdkConstants.ABI_ARMEABI_V7A,
            SdkConstants.ABI_ARMEABI,
            SdkConstants.ABI_MIPS);

    private static final List<String> ALL_ABI = ImmutableList.of(
            SdkConstants.ABI_INTEL_ATOM,
            SdkConstants.ABI_INTEL_ATOM64,
            SdkConstants.ABI_ARMEABI_V7A,
            SdkConstants.ABI_ARMEABI,
            SdkConstants.ABI_ARM64_V8A,
            SdkConstants.ABI_MIPS,
            SdkConstants.ABI_MIPS64);

    private NdkExtension ndkExtension;

    private Project project;

    private File ndkDirectory;

    static {
        // Initialize static maps.
        PLATFORM_STRING = ImmutableMap.<String, String>builder()
                .put(SdkConstants.ABI_INTEL_ATOM, "x86")
                .put(SdkConstants.ABI_INTEL_ATOM64, "x86_64")
                .put(SdkConstants.ABI_ARMEABI_V7A, "arm-linux-androideabi")
                .put(SdkConstants.ABI_ARMEABI, "arm-linux-androideabi")
                .put(SdkConstants.ABI_ARM64_V8A, "aarch64-linux-android")
                .put(SdkConstants.ABI_MIPS, "mipsel-linux-android")
                .put(SdkConstants.ABI_MIPS64, "mips64el-linux-android")
                .build();

        ARCHITECTURE_STRING = ImmutableMap.<String, String>builder()
                .put(SdkConstants.ABI_INTEL_ATOM, SdkConstants.CPU_ARCH_INTEL_ATOM)
                .put(SdkConstants.ABI_INTEL_ATOM64, SdkConstants.CPU_ARCH_INTEL_ATOM64)
                .put(SdkConstants.ABI_ARMEABI_V7A, SdkConstants.CPU_ARCH_ARM)
                .put(SdkConstants.ABI_ARMEABI, SdkConstants.CPU_ARCH_ARM)
                .put(SdkConstants.ABI_ARM64_V8A, SdkConstants.CPU_ARCH_ARM64)
                .put(SdkConstants.ABI_MIPS, SdkConstants.CPU_ARCH_MIPS)
                .put(SdkConstants.ABI_MIPS64, SdkConstants.CPU_ARCH_MIPS64)
                .build();

        TOOLCHAIN_STRING = ImmutableMap.<String, String>builder()
                .put("gcc", "")
                .put("clang", "clang")
                .build();
    }

    public NdkHandler(Project project, NdkExtension ndkExtension) {
        this.project = project;
        this.ndkExtension = ndkExtension;
        ndkDirectory = findNdkDirectory(project);
    }

    /**
     * Toolchain name used by the NDK.
     *
     * This is the name of the folder containing the toolchain under $ANDROID_NDK_HOME/toolchain.
     * e.g. for gcc targetting arm64_v8a, this method returns "aarch64-linux-android-4.9".
     */
    private static String getToolchainName(
            String toolchain,
            String toolchainVersion,
            String platform) {
        return PLATFORM_STRING.get(platform) + "-" + TOOLCHAIN_STRING.get(toolchain)
                + toolchainVersion;
    }

    /**
     * Determine the location of the NDK directory.
     *
     * The NDK directory can be set in the local.properties file or using the ANDROID_NDK_HOME
     * environment variable.
     */
    private static File findNdkDirectory(Project project) {
        File rootDir = project.getRootDir();
        File localProperties = new File(rootDir, FN_LOCAL_PROPERTIES);

        if (localProperties.isFile()) {

            Properties properties = new Properties();
            InputStreamReader reader = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                FileInputStream fis = new FileInputStream(localProperties);
                reader = new InputStreamReader(fis, Charsets.UTF_8);
                properties.load(reader);
            } catch (FileNotFoundException ignored) {
                // ignore since we check up front and we don't want to fail on it anyway
                // in case there's an env var.
            } catch (IOException e) {
                throw new RuntimeException("Unable to read ${localProperties}", e);
            } finally {
                try {
                    Closeables.close(reader, true /* swallowIOException */);
                } catch (IOException e) {
                    // ignore.
                }
            }

            String ndkDirProp = properties.getProperty("ndk.dir");
            if (ndkDirProp != null) {
                return new File(ndkDirProp);
            }

        } else {
            String envVar = System.getenv("ANDROID_NDK_HOME");
            if (envVar != null) {
                return new File(envVar);
            }
        }
        return null;
    }

    /**
     * Returns the directory of the NDK.
     */
    @Nullable
    public File getNdkDirectory() {
        return ndkDirectory;
    }

    NdkExtension getNdkExtension() {
        return ndkExtension;
    }

    /**
     * Return the path containing the prebuilt toolchain.
     *
     * @param toolchain        Name of the toolchain ["gcc", "clang"].
     * @param toolchainVersion Version of the toolchain.
     * @param platform         Target platform supported by the NDK.
     * @return Directory containing the prebuilt toolchain.
     */

    public File getToolchainPath(String toolchain, String toolchainVersion, String platform) {
        File prebuiltFolder;
        if (toolchain.equals("gcc")) {
            prebuiltFolder = new File(
                    getNdkDirectory(),
                    "toolchains/" + getToolchainName(toolchain, toolchainVersion, platform)
                            + "/prebuilt");

        } else if (toolchain.equals("clang")) {
            prebuiltFolder = new File(
                    getNdkDirectory(),
                    "toolchains/llvm-" + toolchainVersion + "/prebuilt");
        } else {
            throw new InvalidUserDataException("Unrecognized toolchain: " + toolchain);
        }

        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String hostOs;
        if (osName.contains("windows")) {
            hostOs = "windows";
        } else if (osName.contains("mac")) {
            hostOs = "darwin";
        } else {
            hostOs = "linux";
        }

        // Use 64-bit toolchain if available.
        File toolchainPath = new File(prebuiltFolder, hostOs + "-x86_64");
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        }

        // Fallback to 32-bit if we can't find the 64-bit toolchain.
        toolchainPath = new File(prebuiltFolder, hostOs);
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        } else {
            throw new InvalidUserDataException("Unable to find toolchain prebuilt folder in: "
                    + prebuiltFolder);
        }
    }

    /**
     * Returns the sysroot directory for the toolchain.
     */
    public String getSysroot(Platform platform) {
        return ndkDirectory + "/platforms/" + ndkExtension.getCompileSdkVersion()
                + "/arch-" + ARCHITECTURE_STRING.get(platform.getName());
    }

    /**
     * Return the directory containing prebuilt binaries such as gdbserver.
     */
    public File getPrebuiltDirectory(Platform platform) {
        return new File(
                ndkDirectory, "prebuilt/android-" + ARCHITECTURE_STRING.get(platform.getName()));
    }

    /**
     * Return true if compiledSdkVersion supports 64 bits ABI.
     */
    public boolean supports64Bits() {
        String targetString = getNdkExtension().getCompileSdkVersion().replace("android-", "");
        try {
            return Integer.parseInt(targetString) >= 20;
        } catch (NumberFormatException ignored) {
            // "android-L" supports 64-bits.
            return true;
        }
    }

    /**
     * Return the gcc version that will be used by the NDK.
     *
     * If the gcc toolchain is used, then it's simply the toolchain version requested by the user.
     * If clang is used, then it depends on the version of the NDK.
     */
    public String getGccToolchainVersion() {
        if (ndkExtension.getToolchain().equals("gcc")) {
            return ndkExtension.getToolchainVersion();
        } else {
            return supports64Bits() ? "4.9" : "4.8";
        }
    }

    /**
     * Returns a list of supported ABI.
     */
    public Collection<String> getSupportedAbis() {
        return supports64Bits() ? ALL_ABI : ABI32;
    }
}
