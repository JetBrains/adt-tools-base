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
#include <iostream>
#include <memory>
#include <string>

#include <grpc++/grpc++.h>

#include "perfd/perfa_service.h"
#include "perfd/profiler_service.h"
#include "utils/config.h"

using namespace profiler;
using namespace profiler::utils;

namespace {

void RunServer() {
  grpc::ServerBuilder builder;
  // Listen on the given address without any authentication mechanism.
  builder.AddListeningPort(kServerAddress, grpc::InsecureServerCredentials());

  profiler::ProfilerServiceImpl generic_public_service;
  builder.RegisterService(&generic_public_service);

  profiler::PerfaServiceImpl perfa_service;
  builder.RegisterService(&perfa_service);

  // Finally assemble the server.
  std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
  std::cout << "Server listening on " << kServerAddress << std::endl;

  // Wait for the server to shutdown. Note that some other thread must be
  // responsible for shutting down the server for this call to ever return.
  server->Wait();
}

}  // anonymous namespace

int main(int argc, char** argv) {
  RunServer();

  return 0;
}
