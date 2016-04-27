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

package com.android.build.gradle.internal.ndk;

import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.Toolchain;
import com.android.repository.Revision;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Properties;

/**
 * Handles NDK related information.
 */
public class NdkHandler {

    @Nullable
    private String platformVersion;
    @Nullable
    private String compileSdkVersion;
    private final Toolchain toolchain;
    private final String toolchainVersion;
    private final File ndkDirectory;
    @Nullable
    private final NdkInfo ndkInfo;

    private static final int LATEST_SUPPORTED_VERSION = 12;

    public NdkHandler(
            @NonNull File projectDir,
            @Nullable String platformVersion,
            @NonNull String toolchainName,
            @NonNull String toolchainVersion) {
        this.toolchain = Toolchain.getByName(toolchainName);
        this.toolchainVersion = toolchainVersion;
        this.platformVersion = platformVersion;
        ndkDirectory = findNdkDirectory(projectDir);

        if (ndkDirectory == null) {
            ndkInfo = null;
        } else {
            Revision revision = findRevision();
            if (revision == null) {
                ndkInfo = new DefaultNdkInfo(ndkDirectory);
            } else if (revision.getMajor() > LATEST_SUPPORTED_VERSION) {
                ndkInfo = new NdkR12Info(ndkDirectory);
            } else {
                switch (revision.getMajor()) {
                    case 12:
                        ndkInfo = new NdkR12Info(ndkDirectory);
                        break;
                    default:
                        ndkInfo = new DefaultNdkInfo(ndkDirectory);
                }
            }
        }
    }

    private static Properties readProperties(File file) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, Charsets.UTF_8)) {
            properties.load(reader);
        } catch (FileNotFoundException ignored) {
            // ignore since we check up front and we don't want to fail on it anyway
            // in case there's an env var.
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read %1$s.", file), e);
        }
        return properties;
    }

    @Nullable
    private Revision findRevision() {
        if (ndkDirectory == null) {
            return null;
        } else {
            File sourceProperties = new File(ndkDirectory, "source.properties");
            if (!sourceProperties.exists()) {
                // source.properties does not exist.  It's probably r10.  Use the DefaultNdkInfo.
                return null;
            }
            Properties properties = readProperties(sourceProperties);
            String version = properties.getProperty("Pkg.Revision");
            if (version != null) {
                return Revision.parseRevision(version);
            } else {
                return null;
            }
        }
    }

    @Nullable
    private String getPlatformVersion() {
        if (platformVersion == null && compileSdkVersion != null) {
            checkNotNull(ndkInfo);
            platformVersion = ndkInfo.findLatestPlatformVersion(compileSdkVersion);
        }
        return platformVersion;
    }

    public void setCompileSdkVersion(@NonNull String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
    }

    public Toolchain getToolchain() {
        return toolchain;
    }

    public String getToolchainVersion() {
        return toolchainVersion;
    }

    /**
     * Determine the location of the NDK directory.
     *
     * The NDK directory can be set in the local.properties file or using the ANDROID_NDK_HOME
     * environment variable.
     *
     * Return null if NDK directory is not found.
     */
    @Nullable
    private static File findNdkDirectory(@NonNull File projectDir) {
        File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);

        if (localProperties.isFile()) {

            Properties properties = readProperties(localProperties);
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
     * Return true if NDK directory is configured.
     */
    public boolean isConfigured() {
        return ndkDirectory != null;
    }

    /**
     * Return the directory containing the toolchain.
     *
     * @param toolchain toolchain to use.
     * @param toolchainVersion toolchain version to use.
     * @param abi target ABI of the toolchaina
     * @return a directory that contains the executables.
     */
    @NonNull
    private File getToolchainPath(
            @NonNull Toolchain toolchain,
            @NonNull String toolchainVersion,
            @NonNull Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getToolchainPath(toolchain, toolchainVersion, abi);
    }

    /**
     * Returns the sysroot directory for the toolchain.
     */
    @NonNull
    public String getSysroot(@NonNull Abi abi) {
        if (getPlatformVersion() == null) {
            return "";
        } else {
            checkNotNull(ndkInfo);
            return ndkInfo.getSysrootPath(abi, getPlatformVersion());
        }
    }

    /**
     * Return true if compiledSdkVersion supports 64 bits ABI.
     */
    public boolean supports64Bits() {
        if (getPlatformVersion() == null) {
            return false;
        }
        String targetString = getPlatformVersion().replace("android-", "");
        try {
            return Integer.parseInt(targetString) >= 20;
        } catch (NumberFormatException ignored) {
            // "android-L" supports 64-bits.
            return true;
        }
    }

    /**
     * Return the version of gcc that will be used by the NDK.
     *
     * Gcc is used by clang for linking.  It also contains gnu-libstdc++.
     *
     * If the gcc toolchain is used, then it's simply the toolchain version requested by the user.
     * If clang is used, then it depends the target abi.
     */
    @NonNull
    public String getGccToolchainVersion(@NonNull Abi abi) {
        checkNotNull(ndkInfo);
        return (toolchain == Toolchain.GCC && !toolchainVersion.isEmpty())
                ? toolchainVersion
                : ndkInfo.getDefaultToolchainVersion(Toolchain.GCC, abi);
    }

    /**
     * Return the folder containing gcc that will be used by the NDK.
     */
    @NonNull
    public File getDefaultGccToolchainPath(@NonNull Abi abi) {
        return getToolchainPath(Toolchain.GCC, getGccToolchainVersion(abi), abi);
    }

    /**
     * Returns a list of all ABI.
     */
    @NonNull
    public static Collection<Abi> getAbiList() {
        return ImmutableList.copyOf(Abi.values());
    }

    /**
     * Returns a list of 32-bits ABI.
     */
    @NonNull
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
    @NonNull
    public Collection<Abi> getSupportedAbis() {
        return supports64Bits() ? getAbiList() : getAbiList32();
    }

    /**
     * Return the executable for compiling C code.
     */
    @NonNull
    public File getCCompiler(@NonNull Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getCCompiler(toolchain, toolchainVersion, abi);
    }

    /**
     * Return the executable for compiling C++ code.
     */
    @NonNull
    public File getCppCompiler(@NonNull Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getCppCompiler(toolchain, toolchainVersion, abi);
    }

    /**
     * Return the executable for removing debug symbols from a shared object.
     */
    @NonNull
    public File getStripExecutable(Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getStripExecutable(toolchain, toolchainVersion, abi);
    }

    public StlNativeToolSpecification getStlNativeToolSpecification(
            @NonNull Stl stl,
            @Nullable String stlVersion,
            @NonNull Abi abi) {
        checkNotNull(ndkInfo);
        return ndkInfo.getStlNativeToolSpecification(stl, stlVersion, abi);
    }
}
