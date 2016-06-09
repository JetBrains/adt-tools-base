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
#include "connection_sampler.h"
#include "proto/network_profiler.pb.h"

#include <gtest/gtest.h>

using profiler::ConnectionSampler;

TEST(GetConnectionData, TwoOpenConnectionsWithUidMatched) {
  std::vector<std::string> file_names = {"open_connection_uid_matched1.txt",
                                         "open_connection_uid_matched2.txt"};
  ConnectionSampler collector("12345", file_names);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_connection_data());
  EXPECT_EQ(2, data.connection_data().connection_number());
}

TEST(GetConnectionData, OpenConnectionWithUidUnmatched) {
  std::vector<std::string> file_names = {"open_connection_uid_unmatched.txt"};
  ConnectionSampler collector("12345", file_names);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_connection_data());
  EXPECT_EQ(0, data.connection_data().connection_number());
}

TEST(GetConnectionData, OpenConnectionListeningAllInterfaces) {
  std::vector<std::string> file_names = {
      "open_connection_listening_all_interfaces.txt"};
  ConnectionSampler collector("12345", file_names);
  profiler::proto::NetworkProfilerData data;
  collector.GetData(&data);
  EXPECT_TRUE(data.has_connection_data());
  EXPECT_EQ(0, data.connection_data().connection_number());
}
