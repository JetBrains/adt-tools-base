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
#ifndef SYSTEM_DATA_COLLECTOR_H_
#define SYSTEM_DATA_COLLECTOR_H_

#include <cstdio>

// We are reading the first line of /proc/stat, which contains "cpu  " followed
// by 10 integers. 256 bytes should be enough.
#define LINE_BUFFER_SIZE 256

namespace android_studio_profiler {

struct SystemData;

class SystemDataCollector {
 public:
  // Return true on success.
  bool prepare();
  // Return true on success.
  bool read(SystemData* out);
  // Return true on success.
  bool close();

 private:
  FILE* fp_ = nullptr;
  char line_buffer_[LINE_BUFFER_SIZE] = {};
};

}  // namespace android_studio_profiler
#endif  // SYSTEM_DATA_COLLECTOR_H_
