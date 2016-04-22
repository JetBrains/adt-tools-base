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
#ifndef NETWORK_DATA_COLLECTOR_H_
#define NETWORK_DATA_COLLECTOR_H_

#include <string>
#include <vector>

#include "network_sample_data.h"

namespace network_sampler {

// Abstract network data collector.
class NetworkDataCollector {
 public:
  virtual ~NetworkDataCollector() = default;

  // Returns the type of network sample data to collect.
  virtual NetworkSampleType GetType() const = 0;

  // Returns the app uid that is read from the given pid_status_file if present,
  // -1 otherwise.
  static int GetUid(const std::string &pid_status_file, int pid);

  // Returns true if uid is present in given pid_status_file and appends to the
  // parameter uid string, false otherwise.
  static bool GetUidString(const std::string &pid_status_file, int pid,
                           std::string *uid);

  // Real whole file and split it into lines.
  static bool Read(const std::string& file_path,
                   std::vector<std::string> *lines);

  // Read whole file from beginning, and put output in a single string content.
  static bool Read(const std::string& file_path, std::string *content);

 protected:
  // Returns true if the needed token is found, false otherwise. Tokens are
  // separated by whitespace in given line. token_index does not indicate the
  // character index but indicates the whitespace separated token index.
  // For example, we are looking for the second token from line
  // "Today is Thursday.", the second token "is" exists. But if we specify
  // 4 as the token_index, the needed token is not found.
  static bool FindTokenPosition(const std::string &line, const int token_index,
                                size_t *token_start);

  // Returns true if line contains a substring that is the same as token, and
  // the substring is at given whitespace-separated index token_index.
  static bool CompareToken(const std::string &line, const std::string &token,
                           const int token_index);

 private:
  // Single system call buffer size.
  static const short kBufferSize = 4096;
};

} // namespace network_sampler

#endif // NETWORK_DATA_COLLECTOR_H_
