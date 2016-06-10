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

#ifndef UTILS_PROFILER_PACKAGEMANAGER_H
#define UTILS_PROFILER_PACKAGEMANAGER_H

#include "bash_command.h"

namespace profiler {

// Wrapper around Android executable "pm" (Android Package Manager).
class PackageManager : public BashCommandRunner {
 public:
  PackageManager();

  // Populate path with app apk absolute path (e.g:
  // package:/data/app/com.google.calendar-1/base.apk
  bool GetAppBaseFolder(const std::string &package_name, std::string *path,
                        std::string *error_string) const;

  // Return app data folder absolute path (e.g: /data/data/com.google.calendar/)
  bool GetAppDataPath(const std::string &package_name, std::string *path,
                      std::string *error_string) const;
};
}  // namespace profiler

#endif  // UTILS_PROFILER_PACKAGEMANAGER_H
