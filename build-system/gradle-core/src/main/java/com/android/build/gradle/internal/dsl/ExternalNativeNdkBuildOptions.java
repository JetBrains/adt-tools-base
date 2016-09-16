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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * DSL object for per-variant ndk-build configurations.
 */
public class ExternalNativeNdkBuildOptions implements CoreExternalNativeNdkBuildOptions {
    @NonNull
    private final List<String> arguments = Lists.newArrayList();
    @NonNull
    private final List<String> cFlags = Lists.newArrayList();
    @NonNull
    private final List<String> cppFlags = Lists.newArrayList();
    @NonNull
    private final Set<String> abiFilters = Sets.newHashSet();
    @NonNull
    private final Set<String> targets = Sets.newHashSet();

    /**
     * Per-variant arguments for ndk-build settings also available to your
     * <a href="https://developer.android.com/ndk/guides/android_mk.html">Android.mk</a> and
     * <a href="https://developer.android.com/ndk/guides/application_mk.html">Application.mk</a> scripts.
     * <p>For example:</p>
     * <p><code>arguments "NDK_APPLICATION_MK:=Application.mk"</code></p>
     */
    @NonNull
    @Override
    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(@NonNull List<String> arguments) {
        this.arguments.addAll(arguments);
    }

    public void arguments(@NonNull String ...arguments) {
        Collections.addAll(this.arguments, arguments);
    }

    /**
     * Per-variant flags for the C compiler.
     * <p>For example:</p>
     * <p><code>cFlags "-D_EXAMPLE_C_FLAG1", "-D_EXAMPLE_C_FLAG2"</code></p>
     */
    @NonNull
    @Override
    public List<String> getcFlags() {
        return cFlags;
    }

    public void setcFlags(@NonNull List<String> flags) {
        this.cFlags.addAll(flags);
    }

    public void cFlags(@NonNull String ...flags) {
        Collections.addAll(this.cFlags, flags);
    }

    /**
     * Per-variant flags for the C++ compiler.
     * <p>For example:</p>
     * <p><code>cppFlags "-DTEST_CPP_FLAG1", "-DTEST_CPP_FLAG2"</code></p>
     */
    @NonNull
    @Override
    public List<String> getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(@NonNull List<String> flags) {
        this.cppFlags.addAll(flags);
    }

    public void cppFlags(@NonNull String ...flags) {
        Collections.addAll(this.cppFlags, flags);
    }

    /**
     * Per-variant ABIs Gradle should build, independently of the ones
     * it packages into your APK. In most cases, you only need to specify your desired ABIs using
     * {@link com.android.build.gradle.internal.dsl.NdkOptions:abiFilter android.defaultConfig.ndk.abiFilter},
     * which controls which ABIs Gradle builds and packages into your APK.
     */
    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(@NonNull Set<String> abiFilters) {
        this.abiFilters.addAll(abiFilters);
    }

    public void abiFilters(@NonNull String ...abiFilters) {
        Collections.addAll(this.abiFilters, abiFilters);
    }

    /**
     * Per-variant target libraries from your ndk-build project that Gradle
     * should build and package into your APK.
     * <p>For example, if your ndk-build project defines two
     * libraries, <code>libexample-one.so</code> and <code>libexample-two.so</code>, you can tell
     * Gradle to only build and package <code>libexample-one.so</code> with the following:</p>
     *
     * <p><code>targets "example-one"</code></p>
     *
     * <p>When this property is not configured, Gradle builds and packages all available
     * shared object targets.</p>
     */
    @NonNull
    @Override
    public Set<String> getTargets() {
        return targets;
    }

    public void setTargets(@NonNull Set<String> targets) {
        this.targets.addAll(targets);
    }

    public void targets(@NonNull String ...targets) {
        Collections.addAll(this.targets, targets);
    }
}
