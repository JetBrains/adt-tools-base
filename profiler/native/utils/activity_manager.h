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

#ifndef UTILS_PROFILER_ACTIVITYMANAGER_H
#define UTILS_PROFILER_ACTIVITYMANAGER_H

#include <string>
#include "bash_command.h"

namespace profiler {

// Wrapper around Android executable "am" (Activity Manager).
class ActivityManager : public BashCommandRunner {
 public:
  enum ProfilingMode { SAMPLING, INSTRUMENTED };

  ActivityManager();

  // Starts profiling using ART runtime profiler either by sampling or
  // code instrumentation.
  // Returns true is profiling started successfully. Otherwise false
  // and populate error_string.
  // TODO(sanglardf): Add support for INSTRUMENTED mode, only SAMPLING
  // is supported.
  bool StartProfiling(const ProfilingMode profiling_mode,
                      const std::string &app_package_name,
                      std::string *error_string) const;

  // Stops ongoing profiling. If no profiling was ongoing, this function is a
  // no-op.
  // Returns true is profiling stopped successfully. Otherwise false
  // and populate error_string.
  bool StopProfiling(const std::string &app_package_name,
                     std::string *error_string) const;
};
}  // namespace profiler

#endif  // UTILS_PROFILER_ACTIVITYMANAGER_H
