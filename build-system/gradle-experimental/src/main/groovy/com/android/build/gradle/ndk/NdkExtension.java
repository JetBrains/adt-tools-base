package com.android.build.gradle.ndk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Sets;

import org.gradle.api.Action;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Extension for android-ndk plugin.
 */
public class NdkExtension {

    @NonNull
    private String moduleName = "";

    @NonNull
    private String target = "";

    @NonNull
    private String cFlags = "";

    @NonNull
    private String cppFlags = "";

    @NonNull
    private Set<String> ldLibs;

    @NonNull
    private String toolchain = "";

    @NonNull
    private String toolchainVersion = "";

    @NonNull
    private String stl = "";

    private boolean renderscriptNdkMode = false;

    @NonNull
    private PatternSet cFilePattern;

    @NonNull
    private PatternSet cppFilePattern;

    public NdkExtension() {
        cFilePattern = new PatternSet();
        cppFilePattern = new PatternSet();
        ldLibs = Sets.newHashSet();
    }

    @NonNull
    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(@NonNull String moduleName) {
        this.moduleName = moduleName;
    }

    @NonNull
    public String getCompileSdkVersion() {
        return target;
    }

    public void compileSdkVersion(@NonNull String target) {
        this.target = target;
    }

    public void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel);
    }

    public void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel);
    }

    public void setCompileSdkVersion(@NonNull String target) {
        compileSdkVersion(target);
    }

    @NonNull
    public String getToolchain() {
        return toolchain;
    }

    public void setToolchain(@NonNull String toolchain) {
        this.toolchain = toolchain;
    }

    /**
     * The toolchain version.
     */
    @NonNull
    public String getToolchainVersion() {
        return toolchainVersion;
    }

    public void setToolchainVersion(@NonNull String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    @NonNull
    public String getcFlags() {
        return cFlags;
    }

    public void setcFlags(@NonNull String cFlags) {
        this.cFlags = cFlags;
    }

    @NonNull
    public String getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(@NonNull String cppFlags) {
        this.cppFlags = cppFlags;
    }

    @NonNull
    public Set<String> getLdLibs() {
        return ldLibs;
    }

    @NonNull
    public NdkExtension ldLibs(String lib) {
        ldLibs.add(lib);
        return this;
    }

    @NonNull
    public NdkExtension ldLibs(String... libs) {
        Collections.addAll(ldLibs, libs);
        return this;
    }

    @NonNull
    public NdkExtension setLdLibs(@NonNull Collection<String> libs) {
        ldLibs.clear();
        ldLibs.addAll(libs);
        return this;
    }

    @NonNull
    public String getStl() {
        return stl;
    }

    public void setStl(@NonNull String stl) {
        this.stl = stl;
    }

    public boolean getRenderscriptNdkMode() {
        return renderscriptNdkMode;
    }

    public void setRenderscriptNdkMode(boolean renderscriptNdkMode) {
        this.renderscriptNdkMode = renderscriptNdkMode;
    }

    public void cFilePattern(Action<PatternFilterable> action) {
        action.execute(cFilePattern);
    }

    @NonNull
    public PatternFilterable getCFilePattern() {
        return cFilePattern;
    }

    public void setCFilePattern(@NonNull PatternFilterable pattern) {
        cFilePattern.copyFrom(pattern);
    }

    public void cppFilePattern(Action<PatternFilterable> action) {
        action.execute(cppFilePattern);
    }

    @NonNull
    public PatternFilterable getCppFilePattern() {
        return cppFilePattern;
    }

    public void setCppFilePattern(@NonNull PatternFilterable pattern) {
        cppFilePattern.copyFrom(pattern);
    }
}
