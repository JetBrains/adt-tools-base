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
#include "time_value_buffer.h"

#include <gtest/gtest.h>

using profiler::TimeValue;
using profiler::TimeValueBuffer;

const timespec t1 = {1, 0};
const timespec t2 = {2, 0};
const timespec t3 = {3, 0};
const timespec t4 = {4, 0};

typedef TimeValue<float> TimeFloat;
typedef TimeValueBuffer<float> TimeFloatBuffer;

TEST(AddData, AddDataMoreThanCapacity) {
  TimeFloatBuffer buffer(2);
  buffer.GetSize();
  buffer.Add(10, t1);
  EXPECT_EQ(1, buffer.GetSize());
  EXPECT_EQ(10, buffer.Get(0).value);

  buffer.Add(20, t2);
  EXPECT_EQ(2, buffer.GetSize());
  EXPECT_EQ(20, buffer.Get(1).value);

  buffer.Add(30, t3);
  EXPECT_EQ(2, buffer.GetSize());
  EXPECT_EQ(20, buffer.Get(0).value);
  EXPECT_EQ(30, buffer.Get(1).value);
}

TEST(GetData, Empty) {
  TimeFloatBuffer buffer(3);
  std::vector<TimeFloat> values = buffer.Get(t1, t2);
  EXPECT_EQ(0, values.size());
}

TEST(GetData, DataForQueryTimeRange) {
  TimeFloatBuffer buffer(2);
  buffer.Add(10, t1);
  buffer.Add(20, t2);

  std::vector<TimeFloat> values = buffer.Get(t1, t2);
  EXPECT_EQ(1, values.size());
  EXPECT_EQ(10, values.at(0).value);

  values.clear();
  values = buffer.Get(t1, t3);
  EXPECT_EQ(2, values.size());
  EXPECT_EQ(10, values.at(0).value);
  EXPECT_EQ(20, values.at(1).value);
}

TEST(GetData, NoDataForQueryTimeRange) {
  TimeFloatBuffer buffer(2);
  buffer.Add(10, t1);
  buffer.Add(20, t2);

  std::vector<TimeFloat> values = buffer.Get(t3, t4);
  EXPECT_EQ(0, values.size());
}

TEST(GetData, AddDataMoreThanCapacity) {
  TimeFloatBuffer buffer(2);
  buffer.Add(10, t1);
  buffer.Add(20, t2);
  buffer.Add(30, t3);

  std::vector<TimeFloat> values = buffer.Get(t1, t3);
  EXPECT_EQ(1, values.size());
  EXPECT_EQ(20, values.at(0).value);
}
