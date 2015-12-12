package com.android.build.gradle.managed;

import com.android.annotations.NonNull;

import org.gradle.model.Managed;

/**
 * Root configuration model for android-ndk plugin.
 */
@Managed
public interface NdkConfig extends NdkBuildType {

    /**
     * Platform version of the NDK.
     * The version used is the highest version available in the NDK that is lower or equal to the
     * specified platform version.  If not specified, the highest available version up to the
     * compileSdkVersion is used.
     */
    String getPlatformVersion();
    void setPlatformVersion(@NonNull String platformVersion);

    /**
     * The toolchain version.
     * Support "gcc" or "clang" (default: "gcc").
     */
    String getToolchain();
    void setToolchain(@NonNull String toolchain);

    /**
     * The toolchain version.
     * Set as empty to use the default version for the toolchain.
     */
    String getToolchainVersion();
    void setToolchainVersion(@NonNull String toolchainVersion);
}
