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
#include "traffic_sampler.h"

#include "utils/file_reader.h"

#include <cstdlib>

namespace profiler {

void TrafficSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  int64_t bytes_sent = 0;
  int64_t bytes_received = 0;

  std::vector<std::string> lines;
  FileReader::Read(kFile, &lines);

  for (const std::string &line : lines) {

    if (FileReader::CompareToken(line, kUid, kUidTokenIndex)) {
      size_t receive_token_start = 0;
      if (!FileReader::FindTokenPosition(line, kReceiveBytesTokenIndex,
                                                &receive_token_start)) {
        continue;
      }
      size_t send_token_start = receive_token_start;
      if (!FileReader::FindTokenPosition(
              line, kSendBytesTokenIndex - kReceiveBytesTokenIndex,
              &send_token_start)) {
        continue;
      }

      bytes_sent += strtoll(&line[send_token_start], nullptr, 10);
      bytes_received += strtoll(&line[receive_token_start], nullptr, 10);
    }
  }

  profiler::proto::TrafficData *traffic_data = data->mutable_traffic_data();
  traffic_data->set_bytes_sent(bytes_sent);
  traffic_data->set_bytes_received(bytes_received);
}

}  // namespace profiler
