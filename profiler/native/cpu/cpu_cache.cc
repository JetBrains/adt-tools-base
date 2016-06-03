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
#include "cpu_cache.h"

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <mutex>
#include <vector>

#include "proto/cpu_profiler_data.pb.h"

using profiler::proto::CpuProfilerData;
using std::vector;

namespace profiler {

void CpuCache::Add(const CpuProfilerData& datum) {
  std::lock_guard<std::mutex> lock(cache_mutex_);
  cache_.push_back(datum);
}

vector<CpuProfilerData> CpuCache::Retrieve(int32_t app_id, int64_t from,
                                           int64_t to) {
  std::lock_guard<std::mutex> lock(cache_mutex_);
  vector<CpuProfilerData> filtered;

  for (const auto& datum : cache_) {
    auto id = datum.basic_info().app_id();
    auto timestamp = datum.basic_info().end_timestamp();
    if (id == app_id || app_id == kAnyApp) {
      if (timestamp > from && timestamp <= to) {
        filtered.push_back(datum);
      }
    }
  }

  return filtered;
}

}  // namespace profiler
