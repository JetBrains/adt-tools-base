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
#include "network_collector.h"

#include "network/connection_sampler.h"
#include "network/traffic_sampler.h"
#include "proto/profiler.pb.h"
#include "utils/stopwatch.h"

#include <unistd.h>

using profiler::utils::Stopwatch;

namespace profiler {

NetworkCollector::~NetworkCollector() {
  if (is_running_) {
    StopProfile();
  }
}

void NetworkCollector::StartProfile() {
  if (samplers_.empty()) {
    CreateSamplers();
  }
  if (!is_running_.exchange(true)) {
    profiler_thread_ = std::thread(&NetworkCollector::Collect, this);
  }
}

void NetworkCollector::StopProfile() {
  if (is_running_.exchange(false)) {
    profiler_thread_.join();
  }
}

void NetworkCollector::Collect() {
  Stopwatch stopwatch;
  while (is_running_.load()) {
    for (const auto &sampler : samplers_) {
      profiler::proto::ProfilerData response;
      sampler->GetData(response.mutable_network_data());
      response.set_end_timestamp(stopwatch.GetElapsed());
      service_->save(response);
    }
    usleep(kSleepMicroseconds);
  }
}

void NetworkCollector::CreateSamplers() {
  std::string uid;
  bool has_uid = NetworkSampler::GetUidString(
      NetworkFiles::GetPidStatusFilePath(pid_), &uid);
  if (has_uid) {
    samplers_.emplace_back(
        new TrafficSampler(uid, NetworkFiles::GetTrafficBytesFilePath()));
    samplers_.emplace_back(new ConnectionSampler(
        uid, NetworkFiles::GetConnectionFilePaths()));
  }
}

} // profiler
