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
#ifndef UTILS_TOKEN_H_
#define UTILS_TOKEN_H_

#include <string>
#include <vector>

// TODO(b/29272873): Unify tokenization in profiler C++ code base.

namespace profiler {

// Returns the tokens by splitting |input| string by |delimiters|.
std::vector<std::string> GetTokens(const std::string& input,
                                   const std::string& delimiters);

}  // namespace profiler

#endif  // UTILS_TOKEN_H_
