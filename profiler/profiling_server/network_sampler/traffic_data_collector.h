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
#ifndef TRAFFIC_DATA_COLLECTOR_
#define TRAFFIC_DATA_COLLECTOR_

#include "network_data_collector.h"
#include "network_sample_data.h"

#include <string>

namespace network_sampler {

// Data collector of network traffic information. For example, it provides sent
// and received bytes of an app.
class TrafficDataCollector : public NetworkDataCollector {
 public:
  TrafficDataCollector(const std::string& file) : kFile(file) {}

  // Reads traffic bytes sent and received into data.
  void ReadBytes(const std::string &uid, NetworkSampleData *data);

  NetworkSampleType GetType() const override;

 private:
  static const int kUidTokenIndex = 3;
  static const int kSendBytesTokenIndex = 7;
  static const int kReceiveBytesTokenIndex = 5;

  const std::string kFile;
};

} // namespace network_sampler

#endif // TRAFFIC_DATA_COLLECTOR_
