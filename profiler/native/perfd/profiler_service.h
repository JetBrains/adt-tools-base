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
#ifndef PROFILER_PERFD_PROFILER_SERVICE_H_
#define PROFILER_PERFD_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>

#include "proto/profiler_service.grpc.pb.h"

namespace profiler {

class ProfilerServiceImpl final
    : public profiler::proto::ProfilerService::Service {
  grpc::Status GetVersion(grpc::ServerContext* context,
                          const profiler::proto::VersionRequest* request,
                          profiler::proto::VersionResponse* reply) override;
};

}  // namespace profiler

#endif  // PROFILER_PERFD_PROFILER_SERVICE_H_
