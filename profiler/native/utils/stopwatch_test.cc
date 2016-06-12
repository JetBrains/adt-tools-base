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
#include "stopwatch.h"

#include <gtest/gtest.h>

using profiler::Clock;
using profiler::Stopwatch;
using std::shared_ptr;

class MockClock final : public Clock {
 public:

  MockClock(uint64_t mockTime = 0) : mockTime_(mockTime) { }

  virtual uint64_t GetCurrentTime() const override {
    return mockTime_;
  }

  void SetCurrentTime(uint64_t time) {
    mockTime_ = time;
  }

  void Elapse(uint64_t elapsed) {
    mockTime_ += elapsed;
  }

 private:
  uint64_t mockTime_;
};

TEST(Stopwatch, GetElapsedTimeFromConstruction) {
  auto clock = std::make_shared<MockClock>(100);
  Stopwatch stopwatch(clock);

  EXPECT_EQ(0, stopwatch.GetElapsed());

  clock->Elapse(123);
  EXPECT_EQ(123, stopwatch.GetElapsed());

  clock->Elapse(9000);
  EXPECT_EQ(9123, stopwatch.GetElapsed());
}

TEST(Stopwatch, GetElapsedTimeFromStart) {
  auto clock = std::make_shared<MockClock>(100);
  Stopwatch stopwatch(clock);

  clock->Elapse(123);

  stopwatch.Start();
  EXPECT_EQ(0, stopwatch.GetElapsed());
  clock->Elapse(321);
  EXPECT_EQ(321, stopwatch.GetElapsed());
}
