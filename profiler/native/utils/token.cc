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
#include "utils/token.h"

// TODO(b/29258672): Add unit tests for token.h/cc.
using std::vector;
using std::string;

namespace profiler {

vector<string> GetTokens(const string& input, const string& delimiters) {
  vector<string> tokens{};

  size_t start = 0;  // Start position of a token;
  size_t end = 0;    // Position of the first delimiters after a token.
  while (true) {
    start = input.find_first_not_of(delimiters, start);
    if (start == string::npos) break;  // No more tokens.
    end = input.find_first_of(delimiters, start + 1);
    if (end == string::npos) {
      // Last token is found.
      tokens.push_back(input.substr(start));
      break;
    } else {
      tokens.push_back(input.substr(start, end - start));
    }
    start = end + 1;
  }
  return tokens;
}

}  // namespace profiler
