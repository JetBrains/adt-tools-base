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
#ifndef STOPWATCH_H_
#define STOPWATCH_H_

#include <cstdint>
#include <memory>

#include "clock.h"

namespace profiler {

// Class for measuring elapsed time
// Example:
//   Stopwatch stopwatch;
//   ... do stuff ...
//   Log(stopwatch.GetElapsed())
//
//   stopwatch.Start();
//   ... do more stuff ...
//   Log(stopwatch.GetElapsed())
class Stopwatch final {
 public:
  Stopwatch();

  // Constructor which can take a custom clock, useful for testing
  Stopwatch(std::shared_ptr<Clock> clock);

  // Start counting time from now.
  //
  // Note: A stopwatch is automatically started upon construction.
  void Start();

  // Returns number of nanoseconds elapsed since either the stopwatch was
  // created or since the last call to Start was made.
  uint64_t GetElapsed() const;
 private:

  std::shared_ptr<Clock> clock_;
  uint64_t start_time_;
};

} // namespace profiler

#endif //STOPWATCH_H_
