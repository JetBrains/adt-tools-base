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
#include "traffic_data_collector.h"

#include <cstdlib>

namespace network_sampler {

void TrafficDataCollector::ReadBytes(const std::string &uid,
                                     NetworkSampleData *data) {
  data->type_ = GetType();
  data->send_bytes_ = 0;
  data->receive_bytes_ = 0;

  std::vector<std::string> lines;
  NetworkDataCollector::Read(kFile, &lines);

  for (const std::string &line : lines) {

    if (NetworkDataCollector::CompareToken(line, uid, kUidTokenIndex)) {
      size_t receive_token_start = 0;
      if (!NetworkDataCollector::FindTokenPosition(
              line, kReceiveBytesTokenIndex, &receive_token_start)) {
        continue;
      }
      size_t send_token_start = receive_token_start;
      if (!NetworkDataCollector::FindTokenPosition(
              line, kSendBytesTokenIndex - kReceiveBytesTokenIndex,
              &send_token_start)) {
        continue;
      }

      data->send_bytes_ += strtoll(&line[send_token_start], nullptr, 10);
      data->receive_bytes_ += strtoll(&line[receive_token_start], nullptr, 10);
    }
  }
}

NetworkSampleType TrafficDataCollector::GetType() const {
  return NetworkSampleType::TRAFFIC;
}

} // namespace network_sampler
