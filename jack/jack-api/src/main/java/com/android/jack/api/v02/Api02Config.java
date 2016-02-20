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

package com.android.jack.api.v02;

import com.android.jack.api.v01.Api01Config;
import com.android.jack.api.v01.ConfigurationException;

import java.io.File;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * A configuration for API level 02 of the Jack compiler compatible with API level 01
 */
public interface Api02Config extends Api01Config {
  /**
   * Sets the Java source version (from 3 to 8).
   * @param javaSourceVersion the Java source version
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setJavaSourceVersion(@Nonnull JavaSourceVersion javaSourceVersion)
      throws ConfigurationException;

  /**
   * Sets the verbosity level.
   * @param verbosityLevel the verbosity level
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setVerbosityLevel(@Nonnull VerbosityLevel verbosityLevel) throws ConfigurationException;

  /**
   * Sets the minimum Android API level compatibility.
   * @param level the API level
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setAndroidMinApiLevel(@Nonnegative int level) throws ConfigurationException;

  /**
   * Sets the base directory all relative paths will be based on.
   * @param baseDir the base directory
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  void setBaseDirectory(@Nonnull File baseDir) throws ConfigurationException;
}
