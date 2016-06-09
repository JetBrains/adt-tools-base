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
#ifndef CPU_CPU_USAGE_SAMPLER_H_
#define CPU_CPU_USAGE_SAMPLER_H_

#include <cstdint>
#include <mutex>
#include <unordered_set>

#include "proto/cpu_profiler_service.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class CpuCache;

class CpuUsageSampler {
 public:
  // Creates a CPU usage data collector that saves data to |cpu_cache|.
  CpuUsageSampler(CpuCache* cpu_cache) : cache_(*cpu_cache) {}

  // Starts collecting usage data for process with ID of |pid|, if not already.
  profiler::proto::CpuStartResponse::Status AddProcess(int32_t pid);

  // Stops collecting usage data for process specified by ID |pid|. Does nothing
  // if |pid| is not being monitored.
  profiler::proto::CpuStopResponse::Status RemoveProcess(int32_t pid);

  // Samples CPU data of all processes that need monitoring. Saves the data to
  // |cache_|. Returns true if successfully sampling all processes.
  bool Sample();

 private:
  // Samples the CPU data of a process, including the system-wide usage as a
  // context for this process' usage percentage. Returns true on success.
  // TODO: Handle the case if there is no running process of |pid|.
  bool SampleAProcess(int32_t pid);

  // PIDs of app process that are being profiled.
  std::unordered_set<int32_t> pids_{};
  std::mutex pids_mutex_;
  // Cache where collected data will be saved.
  CpuCache& cache_;
  // Clock that timestamps sample data.
  profiler::SteadyClock clock_;
};

}  // namespace profiler

#endif  // CPU_CPU_USAGE_SAMPLER_H_
