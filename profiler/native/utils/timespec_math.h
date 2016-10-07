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
#ifndef TIMESPEC_MATH_H_
#define TIMESPEC_MATH_H_

#include <time.h>

namespace profiler {

// Class of Math operations on timespec objects. The operation result is
// formalized, its nanosecond value should be non-negative and less than
// 1e9. See also spec in
// http://www.gnu.org/software/libc/manual/html_node/Elapsed-Time.html
class TimespecMath {
 public:
  static void Add(const timespec &x, const timespec &y, timespec *result) {
    result->tv_sec = x.tv_sec + y.tv_sec;
    result->tv_nsec = x.tv_nsec + y.tv_nsec;
    formalize(result);
  }

  static void Subtract(const timespec &x, const timespec &y, timespec *result) {
    result->tv_sec = x.tv_sec - y.tv_sec;
    result->tv_nsec = x.tv_nsec - y.tv_nsec;
    formalize(result);
  }

  // Returns -1 if x is smaller than y, or 0 if x is equal to y, or 1 if x is
  // larger than y.
  static int Compare(const timespec &x, const timespec &y) {
    timespec x1(x);
    timespec y1(y);
    formalize(&x1);
    formalize(&y1);

    if (x1.tv_sec == y1.tv_sec) {
      return x1.tv_nsec < y1.tv_nsec ? -1 : x1.tv_nsec == y1.tv_nsec ? 0 : 1;
    }
    return x1.tv_sec < y1.tv_sec ? -1 : 1;
  }

  // Formalizes a given timespace, and makes the nanosecond value non-negative
  // and no larger than 1e9.
  static void formalize(timespec *result) {
    // Sequence of these two if clause matters. result->tv_nsec may be equal to
    // 1e9 after the first if clause, the second if fixes it.
    if (result->tv_nsec < 0) {
      int sec = result->tv_nsec / 1e9 - 1;
      result->tv_sec += sec;
      result->tv_nsec -= 1e9 * sec;
    }
    if (result->tv_nsec >= 1e9) {
      int sec = result->tv_nsec / 1e9;
      result->tv_sec += sec;
      result->tv_nsec -= 1e9 * sec;
    }
  }
};

}  // namespace profiler

#endif // TIMESPEC_MATH_H_
