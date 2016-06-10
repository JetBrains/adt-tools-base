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
#include "cpu_usage_sampler.h"

#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <sstream>  // for std::ostringstream
#include <string>
#include <vector>

#include "cpu/cpu_cache.h"
#include "proto/cpu_profiler_data.pb.h"
#include "proto/profiler.pb.h"
#include "utils/file_reader.h"
#include "utils/token.h"

using profiler::proto::CpuProfilerData;
using profiler::proto::CpuStartResponse;
using profiler::proto::CpuStopResponse;
using profiler::proto::CpuUsageData;
using profiler::proto::ProfilerData;
using profiler::FileReader;
using std::string;
using std::vector;

namespace {
// Entity knowing the time unit (used by /proc/* files) in milliseconds.
class TimeUnitInMillis {
 public:
  TimeUnitInMillis() : time_unit_in_millis_(-1) {
    int64_t user_hz = sysconf(_SC_CLK_TCK);
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
bool ReadProcStat(string* content) {
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
bool ParseProcStatForUsageData(const string& content, CpuUsageData* data) {
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
  string buffer;
  if (ReadProcStat(&buffer)) {
    return ParseProcStatForUsageData(buffer, data);
  }
  return false;
}

// Reads /proc/[pid]/stat file. Returns true on success.
// TODO: Mock this file on non-Linux platforms.
bool ReadProcPidStat(int32_t pid, std::string* content) {
  // TODO: Use std::to_string() after we use libc++. NDK doesn't support itoa().
  std::ostringstream os;
  os << "/proc/" << pid << "/stat";
  return FileReader::Read(os.str(), content);
}

// Parses a process's stat file (proc/[pid]/stat) to collect info. Returns
// true on success.
// The file has only one line, including a number of fields. The fields are
// numbered from 1. A process usage is the sum of the following fields.
//    (14) utime  %lu
//    (15) stime  %lu
//    (16) cutime  %ld
//    (17) cstime  %ld
//
// The following fields are read, although they are not part of usage.
//    (1) pid  %d       -- Used by this function for sanity check.
//    (2) comm  %s      -- Used to map fields to tokens.
//
// The following fields are part of usage, but they are included by utime
// and cutime, respectively. Therefore, they are not read.
//    (43) guest_time  %lu  (since Linux 2.6.24)
//    (44) cguest_time  %ld  (since Linux 2.6.24)
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ParseProcPidStatForUsageData(int32_t pid, const string& content,
                                  CpuUsageData* data) {
  // Find the start and end positions of the second field.
  // The number of words in the file is variable. The second field is the
  // file name of the executable, in parentheses. The file name could include
  // spaces, so if we blindly split the entire line, it would be hard to map
  // words to fields.
  size_t left_parentheses = content.find_first_of('(');
  size_t right_parentheses = content.find_first_of(')');
  if (left_parentheses == string::npos || right_parentheses == string::npos ||
      right_parentheses <= left_parentheses || left_parentheses == 0)
    return false;

  // Sanity check on pid.
  // TODO: Use std::stoi() after we use libc++, and remove '.c_str()'.
  int32_t pid_from_file = atoi(content.substr(0, left_parentheses - 1).c_str());
  if (pid_from_file != pid) return false;

  // Each token after the right parenthesis is a field, either a charactor or a
  // number. The first token is field #3.
  vector<string> tokens =
      profiler::GetTokens(content.substr(right_parentheses + 1), " \n");
  if (tokens.size() >= 15) {
    // TODO: Use std::stoll() after we use libc++, and remove '.c_str()'.
    int64_t utime = atol(tokens[11].c_str());
    int64_t stime = atol(tokens[12].c_str());
    int64_t cutime = atol(tokens[13].c_str());
    int64_t cstime = atol(tokens[14].c_str());
    int64_t usage_in_time_units = utime + stime + cutime + cstime;
    data->set_app_cpu_time_in_millisec(usage_in_time_units *
                                       time_unit_in_millis.get());
    return true;
  }
  return false;
}

// TODO:
// Parses a thread's stat file (proc/[pid]/task/[tid]/stat) to collect info.
// Returns true on success.
// For a thread, the following fields are read (the first field is numbered as
// 1).
//    (1) id  %d                      => For sanity checking.
//    (2) comm  %s (in parentheses)   => Output |name|.
//    (3) state  %c                   => Output |state|.

bool CollectProcessUsageData(int32_t pid, CpuUsageData* data) {
  string buffer;
  if (ReadProcPidStat(pid, &buffer)) {
    return ParseProcPidStatForUsageData(pid, buffer, data);
  }
  return false;
}

}  // namespace

namespace profiler {

CpuStartResponse::Status CpuUsageSampler::AddProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.insert(pid);
  return CpuStartResponse::SUCCESS;
}

CpuStopResponse::Status CpuUsageSampler::RemoveProcess(int32_t pid) {
  std::lock_guard<std::mutex> lock(pids_mutex_);
  pids_.erase(pid);
  return CpuStopResponse::SUCCESS;
}

bool CpuUsageSampler::Sample() {
  std::unordered_set<int32_t> pids;
  {
    // Make a copy of all processes that need a sample. We want to be
    // thread-safe, and we don't want to hold the lock for too long.
    std::lock_guard<std::mutex> lock(pids_mutex_);
    pids = pids_;
  }
  bool all_succeeded = true;
  for (const int32_t pid : pids) {
    bool process_succeeded = SampleAProcess(pid);
    if (!process_succeeded) all_succeeded = false;
  }
  return all_succeeded;
}

// We sample system-wide usage data each time when we sample a process's usage
// data. This is not a waste. It takes non-trial amount of time to sample
// a process's usage data (> 1 millisecond), and therefore it is better to get
// the up-to-date system-wide data each time.
bool CpuUsageSampler::SampleAProcess(int32_t pid) {
  CpuProfilerData data;
  if (!CollectSystemUsageData(data.mutable_cpu_usage())) return false;
  if (!CollectProcessUsageData(pid, data.mutable_cpu_usage())) return false;
  data.mutable_basic_info()->set_app_id(pid);
  data.mutable_basic_info()->set_end_timestamp(clock_.GetCurrentTime());
  cache_.Add(data);
  return true;
}

}  // namespace profiler
