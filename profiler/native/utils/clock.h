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
#ifndef CLOCK_H_
#define CLOCK_H_

#include <cstdint>

namespace profiler {

// A mockable clock class for getting the current epoch time, in nanoseconds.
// Example:
//  SteadyClock clock;
//  Log(clock.GetCurrentTime())
//
// Note: If you are more interested in the amount of time an operation took,
// rather than absolute time, use Stopwatch instead.
class Clock {
 public:
  // Returns a monotonically increasing value. This value is meant for
  // comparing two relative times, as the time represented by time=0 is not
  // explicitly defined.
  virtual uint64_t GetCurrentTime() const = 0;
  virtual ~Clock() = default;
};

// A Clock implementation based on clock_gettime(CLOCK_MONOTONIC).
//
// Note: we choose to rely on our own class instead of <chrono> because our most
// important use-case is profiling on Android, and this approach lets us use an
// API which:
// - has satisfactory precision, granularity, and reliability
// - is also accessible from Java via System.nanoTime.
// - is used by the Linux kernel to timestamp events (like in perfs)
class SteadyClock final : public Clock {
 public:
  virtual uint64_t GetCurrentTime() const override;
};

} // namespace profiler

#endif //CLOCK_H_
