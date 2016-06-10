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

#ifndef UTILS_PROFILER_BASH_COMMAND_RUNNER_H
#define UTILS_PROFILER_BASH_COMMAND_RUNNER_H

#include <string>

namespace profiler {

// Run bash commands.
class BashCommandRunner {
 public:
  // Expected executable_path can be either absolute, relative or even
  // executable name only path.
  BashCommandRunner(const std::string &executable_path);
  bool Run(const std::string &parameters, std::string *output) const;
  bool RunAs(const std::string &parameters, const std::string &username,
             std::string *output) const;
  static bool IsRunAsCapable();

 private:
  const std::string executable_path_;
  bool RunAndReadOutput(const std::string &cmd, std::string *output) const;
};
}  // namespace profiler
#endif  // UTILS_PROFILER_BASH_COMMAND_RUNNER_H
