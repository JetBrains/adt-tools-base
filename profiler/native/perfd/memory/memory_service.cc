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
#include "memory_service.h"

using namespace ::profiler::proto;

namespace profiler {

::grpc::Status MemoryServiceImpl::GetData(
      ::grpc::ServerContext* context,
      const MemoryRequest* request,
      MemoryData* response) {
  int32_t app_id = request->app_id();
  uint64_t start_time = request->start_time();
  uint64_t end_time = request->end_time();

  response->mutable_profiler_data()->set_app_id(app_id);
  response->mutable_profiler_data()->set_end_timestamp(clock_.GetCurrentTime());
  memory_data_cache_.loadMemorySamples(response, app_id, start_time, end_time);
  memory_data_cache_.loadInstanceCountSamples(response, app_id, start_time, end_time);
  memory_data_cache_.loadGcSamples(response, app_id, start_time, end_time);

  return ::grpc::Status::OK;
}

} // namespace profiler