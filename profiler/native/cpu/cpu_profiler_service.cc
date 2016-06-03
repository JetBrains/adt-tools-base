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
#include "cpu_profiler_service.h"

#include <grpc++/grpc++.h>
#include <vector>

#include "cpu/cpu_cache.h"
#include "proto/cpu_profiler_data.grpc.pb.h"
#include "proto/cpu_profiler_service.grpc.pb.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilerData;
using std::vector;

namespace profiler {

Status CpuProfilerServiceImpl::GetData(grpc::ServerContext* context,
                                       const CpuDataRequest* request,
                                       CpuDataResponse* response) {
  int64_t id_in_request = request->app_id();
  int64_t id = (id_in_request == CpuDataRequest::ANY_APP ? CpuCache::kAnyApp
                                                         : id_in_request);
  const vector<CpuProfilerData>& data =
      cache_.Retrieve(id, request->start_timestamp(), request->end_timestamp());
  for (const auto& datum : data) {
    *(response->add_data()) = datum;
  }
  return Status::OK;
}

}  // namespace profiler
