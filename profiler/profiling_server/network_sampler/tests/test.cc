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
#include <iostream>

#include "../connection_data_collector.h"
#include "../network_files.h"
#include "../traffic_data_collector.h"

using network_sampler::ConnectionDataCollector;
using network_sampler::NetworkFiles;
using network_sampler::NetworkSampleData;
using network_sampler::TrafficDataCollector;

static void PrintData(NetworkSampleData &data) {
  printf("Data: type %d connections %d bytes_sent %lld bytes_received %lld\n",
         data.type_, data.connections_, data.send_bytes_, data.receive_bytes_);
}

int main(int argc, char *argv[]) {

  std::string uid("10007");
  NetworkSampleData data;

  TrafficDataCollector traffic_collector(
      NetworkFiles::GetTrafficBytesFilePath());
  traffic_collector.ReadBytes(uid, &data);
  PrintData(data);

  ConnectionDataCollector connection_collector(
      NetworkFiles::GetConnectionFilePaths());
  connection_collector.ReadConnectionNumber(uid, &data);
  PrintData(data);

  return 0;
}
