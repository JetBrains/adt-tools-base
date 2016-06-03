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
#include "cpu_usage_data_collector.h"

#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <vector>

#include "cpu/cpu_cache.h"
#include "proto/cpu_profiler_data.pb.h"
#include "proto/profiler.pb.h"
#include "utils/file_reader.h"
#include "utils/timestamp.h"

using profiler::proto::CpuProfilerData;
using profiler::proto::CpuUsageData;
using profiler::proto::ProfilerData;
using profiler::utils::FileReader;

namespace {

// Entity knowing the time unit (used by /proc/* files) in milliseconds.
class TimeUnitInMillis {
 public:
  TimeUnitInMillis() : time_unit_in_millis_(-1) {
    long user_hz = sysconf(_SC_CLK_TCK);
    // TODO: Handle other USER_HZ values.
    if (user_hz == 100) {
      time_unit_in_millis_ = 10;
    } else if (user_hz == 1000) {
      time_unit_in_millis_ = 1;
    }
  }

  // Returns the operating system's time unit in milliseconds.
  int64_t get() const { return time_unit_in_millis_; }

 private:
  int64_t time_unit_in_millis_;
};
const TimeUnitInMillis time_unit_in_millis;

const char* const proc_stat_filename = "/proc/stat";

// Reads /proc/stat file. Returns true on success.
// TODO: Mock this file on non-Linux platforms.
bool ReadProcStat(std::string* content) {
  return FileReader::Read(proc_stat_filename, content);
}

// Parses /proc/stat content in |content| and calculates
// |system_cpu_time_in_millisec| and |elapsed_time_in_millisec|. Returns true
// on success.
//
// |elapsed_time_in_millisec| is the combination of every
// state; while |system_cpu_time_in_millisec| is anything but 'idle'.
//
// Only the first line of /proc/stat is used.
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ParseProcStatForUsageData(const std::string& content, CpuUsageData* data) {
  int64_t user, nice, system, idle, iowait, irq, softirq, steal, guest,
      guest_nice;
  // TODO: figure out why sscanf_s cannot compile.
  if (sscanf(content.c_str(),
             "cpu  %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64
             " %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64 " %" PRId64,
             &user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal,
             &guest, &guest_nice) == 10) {
    int64_t load = user + nice + system + iowait + irq + softirq + steal +
                   guest + guest_nice;
    data->set_system_cpu_time_in_millisec(load * time_unit_in_millis.get());
    int64_t elapsed = load + idle;
    data->set_elapsed_time_in_millisec(elapsed * time_unit_in_millis.get());
    return true;
  }
  return false;
}

// Collects system-wide data by reading /proc/stat. Returns true on success.
bool CollectSystemUsageData(CpuUsageData* data) {
  std::string buffer;
  if (ReadProcStat(&buffer)) {
    return ParseProcStatForUsageData(buffer, data);
  }
  return false;
}

// TODO:
// Parses a process's stat file (proc/[pid]/stat) to collect info. Returns
// true on success.
// For a process, the following fields are read (the first field is numbered
// as 1).
//    (1) pid  %d                     => For sanity checking.
//    (2) comm  %s (in parentheses)   => Output |name|.
//    (20) num_threads  %ld           => Output |num_threads|.
//
// A process usage is the sum of the following fields.
//    (14) utime  %lu
//    (15) stime  %lu
//    (16) cutime  %ld
//    (17) cstime  %ld
//    (43) guest_time  %lu  (since Linux 2.6.24)
//    (44) cguest_time  %ld  (since Linux 2.6.24)

// TODO:
// Parses a thread's stat file (proc/[pid]/task/[tid]/stat) to collect info.
// Returns true on success.
// For a thread, the following fields are read (the first field is numbered as
// 1).
//    (1) id  %d                      => For sanity checking.
//    (2) comm  %s (in parentheses)   => Output |name|.
//    (3) state  %c                   => Output |state|.

}  // namespace

namespace profiler {

void CpuUsageDataCollector::AddProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.insert(pid);
}

void CpuUsageDataCollector::RemoveProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.erase(pid);
}

bool CpuUsageDataCollector::Collect() const {
  CpuProfilerData data;
  data.mutable_basic_info()->set_end_timestamp(
      profiler::utils::GetCurrentTime());
  if (CollectSystemUsageData(data.mutable_cpu_usage())) {
    cache_.Add(data);
    return true;
  }
  return false;
}

}  // namespace profiler
