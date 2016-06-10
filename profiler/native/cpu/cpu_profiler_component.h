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
#ifndef CPU_CPU_PROFILER_COMPONENT_H_
#define CPU_CPU_PROFILER_COMPONENT_H_

#include "cpu/cpu_cache.h"
#include "cpu/cpu_collector.h"
#include "cpu/cpu_profiler_service.h"
#include "perfd/profiler_component.h"

namespace profiler {

class CpuProfilerComponent final : public ProfilerComponent {
 public:
  // Creates a CPU perfd component and starts sampling right away.
  CpuProfilerComponent() { collector_.Start(); }

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., perfa).
  grpc::Service* GetInternalService() override { return nullptr; }

 private:
  CpuCache cache_;
  CpuUsageSampler data_sampler_{&cache_};
  // Sampling interval is 100000 microseconds, which equals to 0.1 second.
  CpuCollector collector_{100000, &data_sampler_};
  CpuProfilerServiceImpl public_service_{&cache_, &data_sampler_};
};

}  // namespace profiler

#endif  // CPU_CPU_PROFILER_COMPONENT_H_
