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
#ifndef MEMORY_COLLECTOR_H_
#define MEMORY_COLLECTOR_H_

#include "proto/memory.grpc.pb.h"

#include <atomic>
#include <thread>

#define MS_TO_US 1000

namespace profiler {

class MemoryCollector {
public:
  MemoryCollector(int pid) : pid_(pid) {}
  ~MemoryCollector();

  void StartCollector();
  void StopCollector();
  void CreateSamplers();

private:
  static const int kSleepUs = 300 * MS_TO_US;

  std::thread server_thread_;
  std::atomic_bool is_running_;
  int pid_;

  void CollectorMain();
}; // MemoryCollector

} // namespace profiler

#endif // MEMORY_COLLECTOR_H_
