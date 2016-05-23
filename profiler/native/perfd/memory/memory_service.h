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
#ifndef PROFILER_PERFD_MEMORY_MEMORY_SERVICE_H_
#define PROFILER_PERFD_MEMORY_MEMORY_SERVICE_H_

#include <grpc++/grpc++.h>

#include "memory_data_cache.h"
#include "proto/memory.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class MemoryServiceImpl final : public ::profiler::proto::MemoryService::Service {
public:
  MemoryServiceImpl() = default;
  virtual ~MemoryServiceImpl() = default;

  virtual ::grpc::Status GetData(
      ::grpc::ServerContext* context,
      const ::profiler::proto::MemoryRequest* request,
      ::profiler::proto::MemoryData* response);

private:
  MemoryDataCache memory_data_cache_;
  SteadyClock clock_;
};

}

#endif // PROFILER_PERFD_MEMORY_MEMORY_SERVICE_H_