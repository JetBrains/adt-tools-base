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

int NetworkDataCollector::GetUid(const std::string &data_file) {
  std::string uid;
  if (GetUidString(data_file, &uid)) {
    return atoi(uid.c_str());
  }
  return -1;
}

bool NetworkDataCollector::GetUidString(const std::string &data_file,
                                        std::string *uid_result) {
  std::string content;
  utils::FileReader::Read(data_file, &content);

  const char *uid_prefix = "Uid:";
  // Find the uid value start position. It's supposed to be after the prefix,
  // also after empty spaces on the same line.
  size_t start = content.find(uid_prefix);
  if (start != std::string::npos) {
    start += strlen(uid_prefix);
    start = content.find_first_not_of(" \t", start);
    if (start != std::string::npos)  {
      // Find the uid end position, which should be empty space or new line,
      // and check the uid value contains 0-9 only.
      size_t end = content.find_first_not_of("0123456789", start);
      if (start != end) {
        if (end == std::string::npos) {
          uid_result->assign(content.c_str() + start);
          return true;
        }
        else if (end == content.find_first_of(" \t\n\f", start)) {
          uid_result->assign(content, start, end - start);
          return true;
        }
      }
    }
  }
  return false;
}

} // namespace network
} // namespace profiler
