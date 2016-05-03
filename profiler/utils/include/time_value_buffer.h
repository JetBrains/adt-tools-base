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
#ifndef TIME_VALUE_BUFFER_H_
#define TIME_VALUE_BUFFER_H_

#include <memory>
#include <mutex>
#include <time.h>
#include <vector>

#include "timespec_math.h"

namespace utils {

// Data per sample. The time field indicates a independent time point when
// value is collected.
template <typename T> struct TimeValue {
  timespec time;
  T value;
};

// Data holder class of time sequential collected information. For example,
// traffic bytes sent and received information are repeated collected. It stores
// data and provides query functionality.
template <typename T> class TimeValueBuffer {
 public:
  TimeValueBuffer(size_t capacity)
      : capacity_(capacity), values_(new TimeValue<T>[capacity_]) {}

  // Add sample value collected at a given time point.
  void Add(T value, const timespec &sample_time) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    size_t index = size_ < capacity_ ? size_ : start_;
    values_[index].time = sample_time;
    values_[index].value = value;
    if (size_ < capacity_) {
      size_++;
    } else {
      start_ = (start_ + 1) % capacity_;
    }
  }

  // Returns data within the given range [time_from, time_to).
  std::vector<TimeValue<T>> Get(const timespec &time_from,
                                const timespec &time_to) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    std::vector<TimeValue<T>> result;
    for (size_t i = 0; i < size_; i++) {
      size_t index = (start_ + i) % capacity_;
      if (TimespecMath::Compare(values_[index].time, time_from) >= 0 &&
          TimespecMath::Compare(values_[index].time, time_to) < 0) {
        result.push_back(values_[index]);
      }
    }
    return result;
  }

  // Returns the number of samples stored.
  size_t GetSize() {
    std::lock_guard<std::mutex> lock(values_mutex_);
    return size_;
  }

  // Returns collected sample data at given index.
  TimeValue<T> Get(size_t index) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    TimeValue<T> result = values_[(start_ + index) % capacity_];
    return result;
  }

 private:
  // Indicates the maximum number of samples it can hold.
  const size_t capacity_;

  // TODO: Temporarily uses dynamic array. It should change to circular buffer.
  std::unique_ptr<TimeValue<T>[]> values_;
  std::mutex values_mutex_;
  size_t size_ = 0;
  size_t start_ = 0;
};
} // namespace utils

#endif // TIME_VALUE_BUFFER_H_
