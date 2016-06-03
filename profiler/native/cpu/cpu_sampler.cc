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
#include "cpu_sampler.h"

#include <unistd.h>
#include <atomic>
#include <thread>

namespace profiler {

CpuSampler::~CpuSampler() {
  if (is_running_.load()) {
    Stop();
  }
}

void CpuSampler::Start() {
  if (!is_running_.exchange(true)) {
    sampler_thread_ = std::thread(&CpuSampler::Collect, this);
  }
}

void CpuSampler::Stop() {
  if (is_running_.exchange(false)) {
    sampler_thread_.join();
  }
}

void CpuSampler::Collect() {
  while (is_running_.load()) {
    collector_.Collect();
    usleep(sampling_interval_in_us_);
  }
}

}  // namespace profiler
