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
#include "connection_data_collector.h"
#include "file_reader.h"

namespace profiler {
namespace network {

void ConnectionDataCollector::GetData(
    profiler::proto::NetworkProfilerData *data) {
  int connection_number = 0;
  for (const std::string &file_name : kConnectionFiles) {
    connection_number += ReadConnectionNumber(kUid, file_name);
  }
  data->mutable_connection_data()->set_connection_number(connection_number);
}

int ConnectionDataCollector::ReadConnectionNumber(const std::string &uid,
                                                  const std::string &file) {
  std::vector<std::string> lines;
  utils::FileReader::Read(file, &lines);

  int result = 0;
  for (const std::string &line : lines) {

    // Filters out the connection listening to all local interfaces, input
    // line should look like " 0: 00000000000000000000000000000000:13B4
    // 00000000000000000000000000000000:0000 0A ...".
    static const std::basic_regex<char> kRegexListeningAllInterfaces =
        std::regex(
            "^[ ]*[0-9]+:[ ]+0+:[0-9A-Fa-f]{4}[ ]+0+:[0-9A-Fa-f]{4}[ ]+0A.+$");
    if (regex_match(line, kRegexListeningAllInterfaces)) {
      continue;
    }

    if (utils::FileReader::CompareToken(line, uid, kUidTokenIndex)) {
      result++;
    }
  }
  return result;
}

}  // namespace network
}  // namespace profiler
