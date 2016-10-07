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
#ifndef CPU_CPU_PROFILER_SERVICE_H_
#define CPU_CPU_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>

#include "cpu/cpu_cache.h"
#include "cpu/cpu_usage_sampler.h"
#include "proto/cpu_profiler_service.grpc.pb.h"

namespace profiler {

// CPU profiler specific service for desktop clients (e.g., Android Studio).
class CpuProfilerServiceImpl final
    : public profiler::proto::CpuProfilerService::Service {
 public:
  CpuProfilerServiceImpl(CpuCache* cpu_cache, CpuUsageSampler* monitor)
      : cache_(*cpu_cache), monitor_(*monitor) {}

  grpc::Status GetData(grpc::ServerContext* context,
                       const profiler::proto::CpuDataRequest* request,
                       profiler::proto::CpuDataResponse* response) override;

  // TODO: Handle the case if there is no such a running process.
  grpc::Status StartMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuStartRequest* request,
      profiler::proto::CpuStartResponse* response) override;

  grpc::Status StopMonitoringApp(
      grpc::ServerContext* context,
      const profiler::proto::CpuStopRequest* request,
      profiler::proto::CpuStopResponse* response) override;

 private:
  // Data cache that will be queried to serve requests.
  CpuCache& cache_;
  // The monitor that samples CPU usage data and thread states.
  CpuUsageSampler& monitor_;
};

}  // namespace profiler

#endif  // CPU_CPU_PROFILER_SERVICE_H_
