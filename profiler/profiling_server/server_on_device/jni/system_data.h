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
#ifndef SYSTEM_DATA_H_
#define SYSTEM_DATA_H_

#include <cinttypes>

namespace android_studio_profiler {

// CPU stat data read from /proc/stat.
// See http://man7.org/linux/man-pages/man5/proc.5.html for documentation.
//
// Note 32 bits may be not large enough. The maximum number it can hold is 2^31
// - 1. If a time unit in /proc/stat is 0.01 second (which is true on most
// architectures), the maximum number equals to 248 days in total. On a 8-core
// CPU, it is 31 days. On a 16-core CPU, it is 15 days.
struct SystemData {
 public:
  std::int64_t user, nice, system, idle, iowait, irq, softirq, steal, guest,
      guest_nice;
};

}  // namespace android_studio_profiler
#endif  // SYSTEM_DATA_H_
