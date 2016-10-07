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

#include "proto/cpu_profiler_data.grpc.pb.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuDataRequest;
using profiler::proto::CpuDataResponse;
using profiler::proto::CpuProfilerData;
using profiler::proto::CpuStartRequest;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopRequest;
using profiler::proto::CpuStopResponse;
using std::vector;

namespace profiler {

Status CpuProfilerServiceImpl::GetData(ServerContext* context,
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

grpc::Status CpuProfilerServiceImpl::StartMonitoringApp(
    ServerContext* context, const CpuStartRequest* request,
    CpuStartResponse* response) {
  auto status = monitor_.AddProcess(request->app_id());
  response->set_status(status);
  return Status::OK;
}

grpc::Status CpuProfilerServiceImpl::StopMonitoringApp(
    ServerContext* context, const CpuStopRequest* request,
    CpuStopResponse* response) {
  auto status = monitor_.RemoveProcess(request->app_id());
  response->set_status(status);
  return Status::OK;
}

}  // namespace profiler
