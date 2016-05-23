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
#ifndef MEMORY_DATA_CACHE_H_
#define MEMORY_DATA_CACHE_H_

#include "proto/memory.pb.h"

#include <cstdint>

namespace profiler {

// Class to provide memory data saving interface, this is an empty definition.
class MemoryDataCache {
public:
  virtual void saveMemorySample(const ::profiler::proto::MemoryData_MemorySample& response);
  virtual void saveInstanceCountSample(const ::profiler::proto::MemoryData_InstanceCountSample& response);
  virtual void saveGcSample(const ::profiler::proto::MemoryData_GcSample& response);

  virtual void loadMemorySamples(
      ::profiler::proto::MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time);
  virtual void loadInstanceCountSamples(
      ::profiler::proto::MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time);
  virtual void loadGcSamples(
      ::profiler::proto::MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time);
};

} // namespace profiler

#endif // MEMORY_DATA_CACHE_H_
