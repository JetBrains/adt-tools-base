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

package com.android.jack.api.v03;

import com.android.jack.api.v01.ConfigurationException;
import com.android.jack.api.v02.Api02Config;

import java.io.File;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * A configuration for API level 03 of the Jack compiler compatible with API level 02
 */
public interface Api03Config extends Api02Config {
  /**
   * Sets names of the Jack plugin to use.
   * @param pluginNames Plugin names
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setPluginNames(@Nonnull List<String> pluginNames) throws ConfigurationException;

  /**
   * Sets the path where to find Jack plugins.
   * @param pluginPath The plugin path as a list
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setPluginPath(@Nonnull List<File> pluginPath) throws ConfigurationException;
}
