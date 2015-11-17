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

import org.gradle.model.Managed;

import java.io.File;
import java.util.List;

/**
 * Managed type for specifying a JSON configurations file.
 */
@Managed
public interface JsonConfigFile {

    /**
     * A JSON data file for configuring the ExternalNativeComponentPlugin.
     */
    File getConfig();
    void setConfig(File file);

    /**
     * Command for generating a JSON data file.
     *
     * If a JSON data file is used to configure this plugin and the data file is generated from
     * another program, the command to generate the data file can be specified such that it will be
     * invoke by the generateBuildData task.
     */
    List<String> getCommand();

    /**
     * Command string for generating a JSON data file.
     *
     * Same as getCommand, but allows command to be specified as a single String.
     */
    String getCommandString();
    void setCommandString(String command);
}
