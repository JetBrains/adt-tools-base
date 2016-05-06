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

#ifndef UTILS_PROFILER_INSTALLER_H
#define UTILS_PROFILER_INSTALLER_H

#include <string>

namespace profiler {

// Install/Uninstall an executable file in app data folder so it can be run-as
// the app user.
class Installer {
 public:
  // input MUST NOT be null.
  Installer(const char *app_package_name);

  // Install (copy) an executable, taking care of renaming and write/read
  // permission.
  bool Install(const std::string &binary_path, std::string *error_string) const;

  // Uninstall (delete) an executable, taking care of write permission.
  bool Uninstall(const std::string &binary_path,
                 std::string *error_string) const;

 private:
  // Generates the absolute path an executable should be located on the
  // filesystem to be run-as.
  // Returns true on success, false on failure.
  // In case of failure, path is cleared and error_string populated.
  bool GetInstallationPath(const std::string &executable_path,
                           std::string *install_path,
                           std::string *error_string) const;

  // Generate the name an executable should have when installed in an app data
  // folder.
  const std::string GetBinaryNameForPackage(
      const std::string &executable_filename) const;

  const std::string app_package_name_;
};
}  // namespace profiler
#endif  // UTILS_PROFILER_INSTALLER_H
