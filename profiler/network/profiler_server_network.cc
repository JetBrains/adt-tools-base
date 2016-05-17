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
#include "profiler_server_network.h"
// TODO: Remove relative path for proto header files.
#include "../proto/profiler_data.pb.h"
#include "connection_data_collector.h"
#include "traffic_data_collector.h"

#include <unistd.h>

namespace profiler {
namespace network {

ProfilerServerNetwork::~ProfilerServerNetwork() {
  if (is_running_) {
    StopProfile();
  }
}

void ProfilerServerNetwork::StartProfile() {
  if (collectors_.empty()) {
    CreateCollectors();
  }
  if (!is_running_.exchange(true)) {
    profiler_thread_ = std::thread(Profile, this);
  }
}

void ProfilerServerNetwork::StopProfile() {
  if (is_running_.exchange(false)) {
    profiler_thread_.join();
  }
}

void ProfilerServerNetwork::Profile(ProfilerServerNetwork *network_profiler) {
  ProfilerServerNetwork *profiler = (ProfilerServerNetwork *)network_profiler;
  uint64_t start_time = GetCurrentTime();
  while (profiler->is_running_.load()) {
    for (const auto &collector : profiler->collectors_) {
      profiler::proto::ProfilerData data;
      collector->GetData(data.mutable_network_data());
      data.set_timestamp(GetCurrentTime() - start_time);
      profiler->service_->save(data);
    }
    usleep(kSleepMicroseconds);
  }
}

void ProfilerServerNetwork::CreateCollectors() {
  std::string uid;
  bool has_uid = NetworkDataCollector::GetUidString(
      NetworkFiles::GetPidStatusFilePath(pid_), pid_, &uid);
  if (has_uid) {
    collectors_.emplace_back(
        new TrafficDataCollector(uid, NetworkFiles::GetTrafficBytesFilePath()));
    collectors_.emplace_back(new ConnectionDataCollector(
        uid, NetworkFiles::GetConnectionFilePaths()));
  }
}

uint64_t ProfilerServerNetwork::GetCurrentTime() {
  timespec time;
  clock_gettime(CLOCK_REALTIME, &time);
  return 1e9 * time.tv_sec + time.tv_nsec;
}

}  // namespace network
}  // namespace profiler
