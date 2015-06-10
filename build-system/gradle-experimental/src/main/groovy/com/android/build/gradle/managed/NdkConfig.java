package com.android.build.gradle.managed;

import com.android.annotations.NonNull;

import org.gradle.model.Managed;
import org.gradle.model.Unmanaged;

import java.util.List;
import java.util.Set;

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

    @Unmanaged
    Set<String> getAbiFilters();
    void setAbiFilters(@NonNull Set<String> filters);

    @Unmanaged
    List<String> getCFlags();
    void setCFlags(@NonNull List<String> cFlags);

    @Unmanaged
    List<String> getCppFlags();
    void setCppFlags(@NonNull List<String> cppFlags);

    @Unmanaged
    List<String> getLdLibs();
    void setLdLibs(@NonNull List<String> ldLibs);

    String getStl();
    void setStl(@NonNull String stl);

    Boolean getRenderscriptNdkMode();
    void setRenderscriptNdkMode(Boolean renderscriptNdkMode);
}
