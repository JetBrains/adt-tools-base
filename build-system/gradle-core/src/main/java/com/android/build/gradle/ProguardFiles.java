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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.builder.Version;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Deals with the default ProGuard files for Gradle.
 */
public class ProguardFiles {

    @VisibleForTesting
    public static final ImmutableSet<String> DEFAULT_PROGUARD_WHITELIST =
            ImmutableSet.of("proguard-android.txt", "proguard-android-optimize.txt");

    /**
     * Creates and returns a new {@link File} with the requested default ProGuard file contents.
     *
     * <p><b>Note:</b> If the file is already there it just returns it.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     *
     * @param name the name of the default ProGuard file.
     * @param project used to determine the output location.
     */
    public static File getDefaultProguardFile(@NonNull String name, @NonNull Project project) {
        if (!DEFAULT_PROGUARD_WHITELIST.contains(name)) {
            throw new IllegalArgumentException(
                    "User supplied default proguard base extension "
                            + "name is unsupported. Valid values are: "
                            + DEFAULT_PROGUARD_WHITELIST);
        }

        return FileUtils.join(
                project.getRootProject().getBuildDir(),
                AndroidProject.FD_INTERMEDIATES,
                "proguard-files",
                name + "-" + Version.ANDROID_GRADLE_PLUGIN_VERSION);
    }

    /**
     * Extracts all default ProGuard files into the build directory.
     */
    public static void extractBundledProguardFiles(@NonNull Project project) throws IOException {
        for (String name : DEFAULT_PROGUARD_WHITELIST) {
            File defaultProguardFile = getDefaultProguardFile(name, project);
            if (!defaultProguardFile.isFile()) {
                extractBundledProguardFile(name, defaultProguardFile);
            }
        }
    }

    @VisibleForTesting
    public static void extractBundledProguardFile(
            @NonNull String name, @NonNull File proguardFile) throws IOException {
        Files.createParentDirs(proguardFile);
        URL proguardURL = ProguardFiles.class.getResource(name);
        URLConnection urlConnection = proguardURL.openConnection();
        urlConnection.setUseCaches(false);
        try (InputStream is = urlConnection.getInputStream()) {
            Files.asByteSink(proguardFile).writeFrom(is);
        }
    }
}
