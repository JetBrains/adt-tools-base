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
#include "memory_collector.h"

#include <unistd.h>

namespace profiler {

MemoryCollector::~MemoryCollector() {
  StopCollector();
}

void MemoryCollector::StartCollector() {
  if (!is_running_.exchange(true)) {
    CreateSamplers();
    server_thread_ = std::thread([this] { this->CollectorMain(); });
  }
}

void MemoryCollector::StopCollector() {
  if (is_running_.exchange(false)) {
    server_thread_.join();
  }
}

void MemoryCollector::CollectorMain() {
  while (is_running_) {
    usleep(kSleepUs);
  }

  is_running_.exchange(false);
}

void MemoryCollector::CreateSamplers() {
}

} // namespace profiler
