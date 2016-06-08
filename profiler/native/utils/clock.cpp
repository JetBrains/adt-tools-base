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
#include "clock.h"

// TODO: Separate mac logic into its own .cc
#ifdef __MACH__
#include <mach/clock.h>
#include <mach/mach.h>
#else
#include <ctime>
#endif

namespace profiler {
namespace utils {

uint64_t SteadyClock::GetCurrentTime() {
#ifdef __MACH__ // OS X does not have clock_gettime, use clock_get_time
  clock_serv_t cclock;
  mach_timespec_t mach_time;
  host_get_clock_service(mach_host_self(), SYSTEM_CLOCK, &cclock);
  clock_get_time(cclock, &mach_time);
  mach_port_deallocate(mach_task_self(), cclock);
  return (uint64_t) (1e9 * mach_time.tv_sec + mach_time.tv_nsec);
#else
  timespec time;
  clock_gettime(CLOCK_MONOTONIC, &time);
  return 1e9 * time.tv_sec + time.tv_nsec;
#endif
}

} // namespace utils
} // namespace profiler
