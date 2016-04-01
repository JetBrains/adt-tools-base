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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either excodess or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.tasks.factory.JavaCompileConfigAction;
import com.google.common.base.Charsets;

import org.gradle.api.JavaVersion;

import java.util.Locale;

/**
 * Java compilation options.
 */
public class CompileOptions {
    private static final String VERSION_PREFIX = "VERSION_";

    @Nullable
    private JavaVersion sourceCompatibility;

    @Nullable
    private JavaVersion targetCompatibility;

    @NonNull
    private String encoding = Charsets.UTF_8.name();

    /** The default value is chosen in {@link JavaCompileConfigAction}. */
    private Boolean incremental = null;

    /** @see #setDefaultJavaVersion(JavaVersion) */
    @NonNull
    @VisibleForTesting
    JavaVersion defaultJavaVersion = JavaVersion.VERSION_1_6;

    /** @see #getSourceCompatibility() */
    public void setSourceCompatibility(@NonNull Object sourceCompatibility) {
        this.sourceCompatibility = convert(sourceCompatibility);
    }

    /**
     * Language level of the java source code.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses. Formats supported are:
     * <ul>
     *      <li><code>"1.6"</code></li>
     *      <li><code>1.6</code></li>
     *      <li><code>JavaVersion.Version_1_6</code></li>
     *      <li><code>"Version_1_6"</code></li>
     * </ul>
     */
    @NonNull
    public JavaVersion getSourceCompatibility() {
        return sourceCompatibility != null ? sourceCompatibility : defaultJavaVersion;
    }

    /** @see #getTargetCompatibility() */
    public void setTargetCompatibility(@NonNull Object targetCompatibility) {
        this.targetCompatibility = convert(targetCompatibility);
    }

    /**
     * Version of the generated Java bytecode.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses. Formats supported are:
     * <ul>
     *      <li><code>"1.6"</code></li>
     *      <li><code>1.6</code></li>
     *      <li><code>JavaVersion.Version_1_6</code></li>
     *      <li><code>"Version_1_6"</code></li>
     * </ul>
     */
    @NonNull
    public JavaVersion getTargetCompatibility() {
        return targetCompatibility != null ? targetCompatibility : defaultJavaVersion;
    }

    /** @see #getEncoding() */
    public void setEncoding(@NonNull String encoding) {
        this.encoding = checkNotNull(encoding);
    }

    /**
     * Java source files encoding.
     */
    @NonNull
    public String getEncoding() {
        return encoding;
    }

    /**
     * Default java version, based on the target SDK. Set by the plugin, not meant to be used in
     * build files by users.
     */
    public void setDefaultJavaVersion(@NonNull JavaVersion defaultJavaVersion) {
        this.defaultJavaVersion = checkNotNull(defaultJavaVersion);
    }

    /**
     * Whether java compilation should use Gradle's new incremental model.
     *
     * <p>This may cause issues in projects that rely on annotation processing etc.
     */
    public Boolean getIncremental() {
        return incremental;
    }

    /** @see #getIncremental() */
    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    /**
     * Converts all possible supported way of specifying a Java version to a {@link JavaVersion}.
     * @param version the user provided java version.
     */
    @NonNull
    private static JavaVersion convert(@NonNull Object version) {
        // for backward version reasons, we support setting strings like 'Version_1_6'
        if (version instanceof String) {
            final String versionString = (String) version;
            if (versionString.toUpperCase(Locale.ENGLISH).startsWith(VERSION_PREFIX)) {
                version = versionString.substring(VERSION_PREFIX.length()).replace('_', '.');
            }
        }
        return JavaVersion.toVersion(version);
    }
}
