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

#include "utils/file_reader.h"

namespace profiler {

void ConnectionSampler::GetData(profiler::proto::NetworkProfilerData *data) {
  int connection_number = 0;
  for (const std::string &file_name : kConnectionFiles) {
    connection_number += ReadConnectionNumber(kUid, file_name);
  }
  data->mutable_connection_data()->set_connection_number(connection_number);
}

int ConnectionSampler::ReadConnectionNumber(const std::string &uid,
                                            const std::string &file) {
  std::vector<std::string> lines;
  FileReader::Read(file, &lines);

  int result = 0;
  for (const std::string &line : lines) {
    if (!IsLocalInterface(line) &&
        FileReader::CompareToken(line, uid, kUidTokenIndex)) {
      result++;
    }
  }
  return result;
}

bool ConnectionSampler::IsLocalInterface(const std::string &connection) {
  std::string::const_iterator it = connection.begin();
  // It's possible to have empty space in the beginning, for example, " 1:" has
  // empty space and "100:" does not have empty space.
  EatSpace(connection, &it);
  bool match =
      IsValidHeading(connection, &it) && EatSpace(connection, &it) &&
      IsAllZerosIpAddress(connection, &it) && EatSpace(connection, &it) &&
      IsAllZerosIpAddress(connection, &it) && EatSpace(connection, &it);
  if (match) {
    if ((connection.end() - it > 2) && (*it) == '0') {
      it++;
      return (*it) == 'A' || (*it) == 'a';
    }
  }
  return false;
}

bool ConnectionSampler::IsValidHeading(const std::string &connection,
                                       std::string::const_iterator *it) {
  if (*it != connection.end() && isdigit(**it)) {
    (*it)++;
    while (*it != connection.end() && isdigit(**it)) {
      (*it)++;
    }
    if (*it != connection.end() && **it == ':') {
      (*it)++;
      return true;
    }
  }
  return false;
}

bool ConnectionSampler::IsAllZerosIpAddress(const std::string &connection,
                                            std::string::const_iterator *it) {
  if (*it != connection.end() && **it == '0') {
    (*it)++;
    while (*it != connection.end() && **it == '0') {
      (*it)++;
    }
    if (*it != connection.end() && **it == ':') {
      (*it)++;
      int count = 0;
      while ((*it != connection.end()) && (isdigit(**it) || isalpha(**it))) {
        count++;
        (*it)++;
      }
      return count == 4;
    }
  }
  return false;
}

bool ConnectionSampler::EatSpace(const std::string &connection,
                                 std::string::const_iterator *it) {
  bool has_empty_space = false;
  while (*it != connection.end() && isspace(**it)) {
    (*it)++;
    if (!has_empty_space) {
      has_empty_space = true;
    }
  }
  return has_empty_space;
}

} // namespace profiler
