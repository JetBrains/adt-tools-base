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
#ifndef NETWORK_CONNECTION_SAMPLER_H
#define NETWORK_CONNECTION_SAMPLER_H

#include "network/network_sampler.h"

#include <string>
#include <vector>

namespace profiler {

// Data collector of open connection information. For example, it can
// collect the number of both tcp and udp open connections.
class ConnectionSampler final : public NetworkSampler {
 public:
  ConnectionSampler(const std::string &uid,
                          const std::vector<std::string> &files)
      : kUid(uid), kConnectionFiles(files) {}

  // Read system file to get the number of open connections, and store data in
  // given {@code NetworkProfilerData}.
  void GetData(profiler::proto::NetworkProfilerData *data) override;

 private:
  // Returns open connection number that is read from a given file.
  int ReadConnectionNumber(const std::string &uid, const std::string &file);

  // Returns whether the connection line is for local interface listening.
  // In other words, both remote and local ip addresses are all zeros and the
  // connection state is listening ('0A'). For example, here is a connection
  // when the return value is TRUE: " 01: 00000000000000000000000000000000:13B4
  // 00000000000000000000000000000000:0000 0A ...".
  bool IsLocalInterface(const std::string &connection);

  // Returns whether the next token starting at the current iterator position is
  // a valid heading which is the same as regex "\s*[0-9]+:". The parameter
  // iterator {@code it} is modified to be at the first char after heading. For
  // example, here is a line returns TRUE: "01:".
  bool IsValidHeading(const std::string &connection,
                      std::string::const_iterator *it);

  // Returns whether the next token starting at the current iterator position is
  // an ip address of all zeros, which is the same as regex "0+:[0-9A-Za-z]{4}".
  // The parameter iterator {@code it} is modified to be at the first character
  // after all zeros ip.
  bool IsAllZerosIpAddress(const std::string &connection,
                           std::string::const_iterator *it);

  // Returns whether the next token starting at the current iterator position is
  // a empty space, and jump parameter {@code it} to the first character that is
  // not empty space.
  bool EatSpace(const std::string &connection, std::string::const_iterator *it);

  // Index indicates the location of app uid(unique id), in the connection
  // system files. One open connection is listed as a line in file. Tokens
  // are joined by whitespace in a line. For example, a connection line is
  // "01: 001:002:123 001:002:001 01 02 03 04 20555...".
  // Index of Uid token "20555" is 7.
  static const int kUidTokenIndex = 7;

  // App uid for parsing file to get app information.
  const std::string kUid;

  // List of files containing open connection data; for example /proc/net/tcp6.
  // Those files contain multiple apps' information.
  const std::vector<std::string> kConnectionFiles;
};

}  // namespace profiler

#endif // NETWORK_CONNECTION_SAMPLER_H
