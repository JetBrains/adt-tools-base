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
#include "memory_data_cache.h"

using namespace ::profiler::proto;

namespace profiler {

void MemoryDataCache::saveMemorySample(
    const MemoryData_MemorySample& response) {
}

void MemoryDataCache::saveInstanceCountSample(
    const MemoryData_InstanceCountSample& response) {
}

void MemoryDataCache::saveGcSample(
    const MemoryData_GcSample& response) {
}

void MemoryDataCache::loadMemorySamples(
    MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time) {
}

void MemoryDataCache::loadInstanceCountSamples(
    MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time) {
}

void MemoryDataCache::loadGcSamples(
    MemoryData* response, int32_t app_id, int64_t start_time, int64_t end_time) {
}

} // namespace profiler
