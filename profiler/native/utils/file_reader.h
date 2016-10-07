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
#ifndef FILE_READER_H_
#define FILE_READER_H_

#include <string>
#include <vector>

namespace profiler {

// Class to read file and perform related tokenize util functions.
class FileReader {
 public:
  // Real whole file and split it into lines.
  static bool Read(const std::string &file_path,
                   std::vector<std::string> *lines);

  // Reads whole file from beginning, and put output in a single string content.
  // Returns true on success.
  // The ownership of |content| is not transferred.
  // TODO(b/29111072): Provide an option to read the entire file in a single
  // access. Multiple reads may cause consistency issues, especially for virtual
  // files such as /proc.stat in Android/Linux.
  static bool Read(const std::string &file_path, std::string *content);

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
  // TODO: Make the buffer size configurable in a constructor.
  static const short kBufferSize_ = 4096;
};

}  // namespace profiler

#endif  // FILE_READER_H_
