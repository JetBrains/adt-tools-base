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
#include "proto/network_profiler.pb.h"
#include "network/traffic_sampler.h"

#include <gtest/gtest.h>

using profiler::TrafficSampler;

TEST(GetTrafficData, OutputIsFromSingleLineEntry) {
  std::string file_name("traffic_uid_matched_single_entry.txt");
  TrafficSampler collector("12345", file_name);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_traffic_data());
  EXPECT_EQ(1111, data.traffic_data().bytes_received());
  EXPECT_EQ(2222, data.traffic_data().bytes_sent());
}

TEST(GetTrafficData, OutputIsSumOfMultiLineEntries) {
  std::string file_name("traffic_uid_matched_multiple_entries.txt");
  TrafficSampler collector("12345", file_name);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_traffic_data());
  EXPECT_EQ(3333, data.traffic_data().bytes_received());
  EXPECT_EQ(6666, data.traffic_data().bytes_sent());
}

TEST(GetTrafficData, OutputIsZeroAsUnmatchUidEntryIsFilteredOut) {
  std::string file_name("traffic_uid_unmatched.txt");
  TrafficSampler collector("12345", file_name);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_traffic_data());
  EXPECT_EQ(0, data.traffic_data().bytes_received());
  EXPECT_EQ(0, data.traffic_data().bytes_sent());
}
