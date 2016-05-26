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
#include "network_data_collector.h"

#include "utils/file_reader.h"

#include <fcntl.h>
#include <sstream>
#include <string.h>
#include <string>
#include <unistd.h>

namespace profiler {
namespace network {

int NetworkDataCollector::GetUid(const std::string &data_file, int pid) {
  std::string uid;
  if (GetUidString(data_file, pid, &uid)) {
    return atoi(uid.c_str());
  }
  return -1;
}

bool NetworkDataCollector::GetUidString(const std::string &data_file, int pid,
                                        std::string *uid_result) {
  std::string content;
  utils::FileReader::Read(data_file.c_str(), &content);

  const char *uid_prefix = "Uid:";
  size_t start_pos = content.find(uid_prefix);
  if (start_pos != std::string::npos) {
    start_pos += strlen(uid_prefix);
    start_pos = content.find_first_not_of(' ', start_pos);
    size_t length = content.find_first_of(' ', start_pos) - start_pos;
    uid_result->append(content, start_pos, length);
    return true;
  }
  return false;
}

}  // namespace network
}  // namespace profiler
