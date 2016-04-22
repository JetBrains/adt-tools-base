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
#ifndef NETWORK_FILES_
#define NETWORK_FILES_

#include <string>
#include <vector>

namespace network_sampler {

class NetworkFiles {
 public:
  static std::string GetPidStatusFilePath(const int pid) {
    return "/proc/" + std::to_string(pid) + "/status";
  }

  // Path of file that contains all apps' sent and received bytes.
  static const std::string& GetTrafficBytesFilePath() {
    static const std::string file_path("/proc/net/xt_qtaguid/stats");
    return file_path;
  }

  // Path of files that contains all apps' open connection numbers.
  static const std::vector<std::string>& GetConnectionFilePaths() {
    static const std::vector<std::string> file_paths{{
      "/proc/net/tcp6",
      "/proc/net/udp6",
      "/proc/net/raw6",
      "/proc/net/tcp",
      "/proc/net/udp",
      "/proc/net/raw",
    }};
    return file_paths;
  }
};

} // namespace network_sampler

#endif // NETWORK_FILES_
