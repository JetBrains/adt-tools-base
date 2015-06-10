package com.android.build.gradle.managed;

import com.android.annotations.NonNull;
import com.android.build.gradle.managed.FilePattern;
import com.android.build.gradle.managed.ManagedString;

import org.gradle.model.Managed;
import org.gradle.model.ModelSet;

/**
 * Configuration model for android-ndk plugin.
 */
@Managed
public interface NdkConfig {

    String getModuleName();
    void setModuleName(@NonNull String moduleName);

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

    ModelSet<ManagedString> getAbiFilters();

    String getCFlags();
    void setCFlags(@NonNull String cFlags);

    String getCppFlags();
    void setCppFlags(@NonNull String cppFlags);

    ModelSet<ManagedString> getLdLibs();

    String getStl();
    void setStl(@NonNull String stl);

    Boolean getRenderscriptNdkMode();
    void setRenderscriptNdkMode(Boolean renderscriptNdkMode);

    @NonNull
    FilePattern getCFilePattern();

    @NonNull
    FilePattern getCppFilePattern();
}
