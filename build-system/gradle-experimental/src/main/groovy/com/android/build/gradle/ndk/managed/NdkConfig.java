package com.android.build.gradle.ndk.managed;

import com.android.annotations.NonNull;
import com.android.build.gradle.managed.FilePattern;
import com.android.build.gradle.managed.ManagedString;

import org.gradle.model.Managed;
import org.gradle.model.collection.ManagedSet;

/**
 * Configuration model for android-ndk plugin.
 */
@Managed
public interface NdkConfig {

    String getModuleName();
    void setModuleName(@NonNull String moduleName);

    String getCompileSdkVersion();
    void setCompileSdkVersion(@NonNull String target);

    String getToolchain();
    void setToolchain(@NonNull String toolchain);

    /**
     * The toolchain version.
     */
    String getToolchainVersion();
    void setToolchainVersion(@NonNull String toolchainVersion);

    String getCFlags();
    void setCFlags(@NonNull String cFlags);

    String getCppFlags();
    void setCppFlags(@NonNull String cppFlags);

    ManagedSet<ManagedString> getLdLibs();

    String getStl();
    void setStl(@NonNull String stl);

    Boolean getRenderscriptNdkMode();
    void setRenderscriptNdkMode(Boolean renderscriptNdkMode);

    @NonNull
    FilePattern getCFilePattern();

    @NonNull
    FilePattern getCppFilePattern();
}
