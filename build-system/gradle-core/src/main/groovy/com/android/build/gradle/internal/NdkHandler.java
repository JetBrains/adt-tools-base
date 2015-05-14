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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.Toolchain;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

import org.gradle.api.InvalidUserDataException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Handles NDK related information.
 */
public class NdkHandler {

    private String compileSdkVersion;
    private final Toolchain toolchain;
    private final String toolchainVersion;

    private File ndkDirectory;

    public NdkHandler(
            @NonNull File projectDir,
            @NonNull String compileSdkVersion,
            @NonNull String toolchainName,
            @NonNull String toolchainVersion) {
        this.compileSdkVersion = compileSdkVersion;
        this.toolchain = Toolchain.getByName(toolchainName);
        this.toolchainVersion = toolchainVersion;
        ndkDirectory = findNdkDirectory(projectDir);
    }

    public String getCompileSdkVersion() {
        return compileSdkVersion;
    }

    public void setCompileSdkVersion(String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
    }

    public Toolchain getToolchain() {
        return toolchain;
    }

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    /**
     * Toolchain name used by the NDK.
     *
     * This is the name of the folder containing the toolchain under $ANDROID_NDK_HOME/toolchain.
     * e.g. for gcc targetting arm64_v8a, this method returns "aarch64-linux-android-4.9".
     */
    public String getToolchainDirectory(Abi abi) {
        return getToolchainDirectory(toolchain, toolchainVersion, abi);
    }

    private String getToolchainDirectory(Toolchain toolchain, String toolchainVersion, Abi abi) {
        String version = toolchainVersion.isEmpty()
                ? getDefaultToolchainVersion(abi)
                : toolchainVersion;
        return abi.getPlatform() + "-" + (toolchain == Toolchain.GCC ? "" : "clang") + version;
    }

    /**
     * Determine the location of the NDK directory.
     *
     * The NDK directory can be set in the local.properties file or using the ANDROID_NDK_HOME
     * environment variable.
     */
    private static File findNdkDirectory(File projectDir) {
        File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);

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
                throw new RuntimeException(String.format("Unable to read %1$s.", localProperties), e);
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

    /**
     * Return the path containing the prebuilt toolchain.
     *
     * @param abi         Target platform supported by the NDK.
     * @return Directory containing the prebuilt toolchain.
     */
    private File getToolchainPath(Abi abi) {
        return getToolchainPath(toolchain, toolchainVersion, abi);
    }


    private File getDefaultGccToolchainPath(Abi abi) {
        return getToolchainPath(Toolchain.GCC, getGccToolchainVersion(abi), abi);
    }

    private File getToolchainPath(Toolchain otherToolchain, String otherToolchainVersion, Abi abi) {

        String version = otherToolchainVersion.isEmpty()
                ? getDefaultToolchainVersion(abi)
                : otherToolchainVersion;

        File prebuiltFolder;
        if (otherToolchain == Toolchain.GCC) {
            prebuiltFolder = new File(
                    getNdkDirectory(),
                    "toolchains/"
                            + getToolchainDirectory(otherToolchain, otherToolchainVersion, abi)
                            + "/prebuilt");

        } else if (otherToolchain == Toolchain.CLANG) {
            prebuiltFolder = new File(
                    getNdkDirectory(),
                    "toolchains/llvm-" + version + "/prebuilt");
        } else {
            throw new InvalidUserDataException("Unrecognized toolchain: " + otherToolchain);
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

        // There should only be one directory in the prebuilt folder.  If there are more than one
        // attempt to determine the right one based on the operating system.
        File[] toolchainPaths = prebuiltFolder.listFiles(
                new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }
                });

        if (toolchainPaths == null) {
            throw new InvalidUserDataException("Unable to find toolchain: "
                    + prebuiltFolder);
        }
        if (toolchainPaths.length == 1) {
            return toolchainPaths[0];
        }

        // Use 64-bit toolchain if available.
        File toolchainPath = new File(prebuiltFolder, hostOs + "-x86_64");
        if (toolchainPath.isDirectory()) {
            return toolchainPath;
        }

        // Fallback to 32-bit if we can't find the 64-bit toolchain.
        String osString = (osName.equals("windows")) ? hostOs : hostOs + "-x86";
        toolchainPath = new File(prebuiltFolder, osString);
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
    public String getSysroot(Abi abi) {
        return ndkDirectory + "/platforms/" + compileSdkVersion+ "/arch-" + abi.getArchitecture();
    }

    /**
     * Return the directory containing prebuilt binaries such as gdbserver.
     */
    public File getPrebuiltDirectory(Abi abi) {
        return new File(ndkDirectory, "prebuilt/android-" + abi.getArchitecture());
    }

    /**
     * Return true if compiledSdkVersion supports 64 bits ABI.
     */
    public boolean supports64Bits() {
        String targetString = compileSdkVersion.replace("android-", "");
        try {
            return Integer.parseInt(targetString) >= 20;
        } catch (NumberFormatException ignored) {
            // "android-L" supports 64-bits.
            return true;
        }
    }

    /**
     * Return the default version of the specified toolchain for a target abi.
     */
    private String getDefaultToolchainVersion(Abi abi) {
        return abi.supports64Bits() ? toolchain.getDefaultVersion64() : toolchain.getDefaultVersion32();
    }

    /**
     * Return the gcc version that will be used by the NDK.
     *
     * If the gcc toolchain is used, then it's simply the toolchain version requested by the user.
     * If clang is used, then it depends the target abi.
     */
    public String getGccToolchainVersion(Abi abi) {
        if (toolchain == Toolchain.GCC) {
            return (toolchainVersion.isEmpty())
                    ? getDefaultToolchainVersion(abi)
                    : toolchainVersion;
        } else {
            return abi.supports64Bits()
                    ? Toolchain.CLANG.getDefaultGccVersion64()
                    : Toolchain.CLANG.getDefaultGccVersion32();
        }
    }

    /**
     * Returns a list of all ABI.
     */
    public static Collection<Abi> getAbiList() {
        return ImmutableList.copyOf(Abi.values());
    }

    /**
     * Returns a list of 32-bits ABI.
     */
    public static Collection<Abi> getAbiList32() {
        ImmutableList.Builder<Abi> builder = ImmutableList.builder();
        for (Abi abi : Abi.values()) {
            if (!abi.supports64Bits()) {
                builder.add(abi);
            }
        }
        return builder.build();
    }

    /**
     * Returns a list of supported ABI.
     */
    public Collection<Abi> getSupportedAbis() {
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    public File getCCompiler(Abi abi) {
        String compiler = toolchain == Toolchain.CLANG ? "clang" : abi.getGccPrefix() + "-gcc";
        return new File(getToolchainPath(abi), "bin/" + compiler);
    }

    public File getCppCompiler(Abi abi) {
        String compiler = toolchain == Toolchain.CLANG ? "clang++" : abi.getGccPrefix() + "-g++";
        return new File(getToolchainPath(abi), "bin/" + compiler);
    }


    public List<File> getStlIncludes(@Nullable String stlName, @NonNull Abi abi) {
        File stlBaseDir = new File(ndkDirectory, "sources/cxx-stl/");
        if (stlName == null || stlName.isEmpty()) {
            stlName = "system";
        } else if (stlName.contains("_")) {
            stlName = stlName.substring(0, stlName.indexOf('_'));
        }

        List<File> includeDirs = Lists.newArrayList();
        if (stlName.equals("system")) {
            includeDirs.add(new File(stlBaseDir, "system/include"));
        } else if (stlName.equals("stlport")) {
            includeDirs.add(new File(stlBaseDir, "stlport/stlport"));
            includeDirs.add(new File(stlBaseDir, "gabi++/include"));
        } else if (stlName.equals("gnustl")) {
            String gccToolchainVersion = getGccToolchainVersion(abi);
            includeDirs.add(new File(stlBaseDir, "gnu-libstdc++/" + gccToolchainVersion + "/include"));
            includeDirs.add(new File(stlBaseDir, "gnu-libstdc++/" + gccToolchainVersion +
                    "/libs/" + abi.getName() + "/include"));
            includeDirs.add(new File(stlBaseDir, "gnu-libstdc++/" + gccToolchainVersion +
                    "/include/backward"));
        } else if (stlName.equals("gabi++")) {
            includeDirs.add(new File(stlBaseDir, "gabi++/include"));
        } else if (stlName.equals("c++")) {
            includeDirs.add(new File(stlBaseDir, "llvm-libc++/libcxx/include"));
            includeDirs.add(new File(stlBaseDir, "gabi++/include"));
            includeDirs.add(new File(stlBaseDir, "../android/support/include"));
        }

        return includeDirs;
    }
}
