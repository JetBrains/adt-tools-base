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
#include <cstdio>
#include <cstdlib>

#include "system_data.h"
#include "system_data_collector.h"

namespace android_studio_profiler {

bool SystemDataCollector::prepare() {
  fp_ = fopen("/proc/stat", "r");
  if (fp_ == nullptr) return false;
  setbuf(fp_, nullptr);
  return true;
}

// Cannot use getline() in Android: "error: undefined reference to 'getline'";
// even though it works on Ubuntu.
//
// TODO: "%lld" should be replaced by something like PRId64, especially if we
// are building for 64 bit platforms. I'm currently having difficulty getting
// it work with the toolchain.
bool SystemDataCollector::read(SystemData* out) {
  if (out == nullptr) return false;
  if (fseek(fp_, 0, SEEK_SET) != 0) return false;

  if (fgets(line_buffer_, sizeof(line_buffer_), fp_) != nullptr) {
    if (sscanf(line_buffer_,
               "cpu  %lld %lld %lld %lld %lld %lld %lld %lld %lld %lld",
               &out->user, &out->nice, &out->system, &out->idle, &out->iowait,
               &out->irq, &out->softirq, &out->steal, &out->guest,
               &out->guest_nice) == 10) {
      return true;
    }
  }
  return false;
}

bool SystemDataCollector::close() {
  if (fp_ != nullptr) {
    fclose(fp_);
    return true;
  }
  return false;
}

}  // namespace android_studio_profiler
