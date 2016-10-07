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

package com.android.jack.api.v01;

import com.android.jack.api.JackConfig;

import java.io.File;
import java.io.PrintStream;

import javax.annotation.Nonnull;

/**
 * A configuration for CLI level 01 of the Jack compiler.
 */
public interface Cli01Config extends JackConfig {

  /**
   * Creates an instance of the {@link Cli01CompilationTask} according to this configuration.
   * @param args To be handled as command line arguments.
   * @return The {@link Cli01CompilationTask}
   * @throws ConfigurationException if something is wrong in Jack's configuration
   */
  @Nonnull
  Cli01CompilationTask getTask(@Nonnull String[] args) throws ConfigurationException;

  /**
   * Redirect Jack's error output to the given stream.
   * @param standardError The stream where to write errors.
   */
  void setStandardError(@Nonnull PrintStream standardError);

  /**
   * Redirect Jack's standards output to the given stream.
   * @param standardOutput The stream where to write non error messages.
   */
  void setStandardOutput(@Nonnull PrintStream standardOutput);

  /**
   * Defines Jack's working directory.
   * @param workingDirectory The base directory that will be used to evaluate non absolute file
   * paths.
   */
  void setWorkingDirectory(@Nonnull File workingDirectory);

}
