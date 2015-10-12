/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.managed;

import com.android.annotations.NonNull;

import org.gradle.model.Managed;
import org.gradle.model.ModelMap;
import org.gradle.model.Unmanaged;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Configuration model for a native project.
 */
@Managed
public interface NativeBuildConfig {

    /**
     * Collection of files that affects the build.
     *
     * These files may be monitored to determine if the JSON data file needs to be updated.
     */
    Set<File> getBuildFiles();

    /**
     * Command for generating a JSON data file.
     *
     * If a JSON data file is used to configure this plugin and the data file is generated from
     * another program, the command to generate the data file can be specified such that it will be
     * invoke by the generateBuildData task.
     */
    List<String> getGeneratorCommand();

    /**
     * A JSON data file for configuring this plugin.
     */
    File getConfigFile();
    void setConfigFile(File configFile);

    /**
     * Arguments to the executable for building the project.
     */
    List<String> getArgs();

    /**
     * Configurations for each native artifact.
     */
    @NonNull
    ModelMap<NativeLibrary> getLibraries();

    /**
     * NDK toolchains used for compilation.
     */
    @NonNull
    ModelMap<NativeToolchain> getToolchains();
}
