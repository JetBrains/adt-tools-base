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

#include <fcntl.h>
#include <sstream>
#include <unistd.h>

namespace network_sampler {

bool NetworkDataCollector::FindTokenPosition(const std::string &line,
                                             const int token_index,
                                             size_t *token_start) {
  int index = -1;
  size_t token_end;
  while (*token_start < line.size()) {
    token_end = line.find_first_of(" \t\r\n\f", *token_start);
    if (token_end == std::string::npos) {
      token_end = line.size();
    }
    if (token_end != *token_start && ++index == token_index) {
      return true;
    }
    *token_start = token_end + 1;
  }
  return false;
}

bool NetworkDataCollector::CompareToken(const std::string &line,
                                        const std::string &token,
                                        const int token_index) {
  size_t start_pos = 0;
  bool is_found = FindTokenPosition(line, token_index, &start_pos);
  return is_found && !line.compare(start_pos, token.length(), token);
}

bool NetworkDataCollector::Read(const std::string &file_path,
                                std::vector<std::string> *lines) {
  std::string content;
  if (Read(file_path, &content)) {
    std::stringstream stream(content);
    std::string line;
    // Check if the last line with no eol-char trailing works
    while (std::getline(stream, line)) {
      lines->push_back(line);
    }
    return true;
  }
  return false;
}

bool NetworkDataCollector::Read(const std::string &file_path,
                                std::string *content) {
  int file = open(file_path.c_str(), O_RDONLY);
  if (file == -1) {
    return false;
  }
  char buffer[kBufferSize];
  off_t offset = 0;
  ssize_t read_size;
  while ((read_size = pread(file, buffer, kBufferSize, offset)) > 0) {
    offset += read_size;
    content->append(buffer, read_size);
  }
  return true;
}

int NetworkDataCollector::GetUid(const std::string &data_file, int pid) {
  std::string uid;
  if (GetUidString(data_file, pid, &uid)) {
    return std::stoi(uid);
  }
  return -1;
}

bool NetworkDataCollector::GetUidString(const std::string &data_file, int pid,
                                        std::string *uid_result) {
  std::string content;
  Read(data_file.c_str(), &content);

  std::string uid_prefix("Uid:");
  size_t start_pos = content.find_first_of(uid_prefix);
  if (start_pos != std::string::npos) {
    start_pos += uid_prefix.size();
    start_pos = content.find_first_not_of(' ', start_pos);
    size_t length = content.find_first_of(' ', start_pos) - start_pos;
    uid_result->append(content, start_pos, length);
    return true;
  }
  return false;
}

} // namespace network_sampler
