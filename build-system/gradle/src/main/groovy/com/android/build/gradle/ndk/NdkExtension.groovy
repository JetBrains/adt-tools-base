/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.ndk

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

/**
 * Extension for android-ndk plugin.
 */
public class NdkExtension {

    private String moduleName;

    private String target

    private String cFlags;

    private String cppFlags;

    private Set<String> ldLibs;

    private String toolchain;

    private String toolchainVersion;

    private String stl;

    private boolean renderscriptNdkMode;

    private PatternSet cFilePattern;

    private PatternSet cppFilePattern;

    public NdkExtension() {
        cFilePattern = new PatternSet()
        cppFilePattern = new PatternSet()
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getCompileSdkVersion() {
        return target
    }

    public void compileSdkVersion(String target) {
        this.target = target
    }

    public void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel)
    }

    public void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel)
    }

    public void setCompileSdkVersion(String target) {
        compileSdkVersion(target)
    }

    public String getToolchain() {
        return toolchain;
    }

    public void setToolchain(String toolchain) {
        this.toolchain = toolchain;
    }

    /**
     * The toolchain version.
     */
    @Nullable
    public String getToolchainVersion() {
        return toolchainVersion;
    }

    public void setToolchainVersion(String toolchainVersion) {
        this.toolchainVersion = toolchainVersion;
    }

    public String getcFlags() {
        return cFlags;
    }

    public void setcFlags(String cFlags) {
        this.cFlags = cFlags;
    }

    public String getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(String cppFlags) {
        this.cppFlags = cppFlags;
    }

    public Set<String> getLdLibs() {
        return ldLibs;
    }

    @NonNull
    public NdkExtension ldLibs(String lib) {
        if (ldLibs == null) {
            ldLibs = Sets.newHashSet();
        }
        ldLibs.add(lib);
        return this;
    }

    @NonNull
    public NdkExtension ldLibs(String... libs) {
        if (ldLibs == null) {
            ldLibs = Sets.newHashSetWithExpectedSize(libs.length);
        }
        Collections.addAll(ldLibs, libs);
        return this;
    }

    @NonNull
    public NdkExtension setLdLibs(Collection<String> libs) {
        if (libs != null) {
            if (ldLibs == null) {
                ldLibs = Sets.newHashSet(libs);
            } else {
                ldLibs.clear();
                ldLibs.addAll(libs);
            }
        } else {
            ldLibs = null;
        }
        return this;
    }

    @Nullable
    public String getStl() {
        return stl;
    }

    public void setStl(String stl) {
        this.stl = stl;
    }

    @Nullable
    public boolean getRenderscriptNdkMode() {
        return renderscriptNdkMode;
    }

    public void setRenderscriptNdkMode(boolean renderscriptNdkMode) {
        this.renderscriptNdkMode = renderscriptNdkMode;
    }

    public void cFilePattern(Action<PatternFilterable> action) {
        action.execute(cFilePattern);
    }

    public PatternFilterable getCFilePattern() {
        return cFilePattern;
    }

    public void setCFilePattern(PatternFilterable pattern) {
        cFilePattern.copyFrom(pattern);
    }

    public void cppFilePattern(Action<PatternFilterable> action) {
        action.execute(cppFilePattern);
    }

    public PatternFilterable getCppFilePattern() {
        return cppFilePattern;
    }

    public void setCppFilePattern(PatternFilterable pattern) {
        cppFilePattern.copyFrom(pattern);
    }
}
