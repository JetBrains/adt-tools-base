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
#include "memory_levels_sampler.h"

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <memory>

namespace {
// dumpsys meminfo command that returns a comma-delimited string within calling process.
const char* kDumpsysCommandFormat = "dumpsys meminfo --local --checkin %d";
const int kCommandMaxLength = 128;
const int kBufferSize = 1024;

enum MemoryType {
  UNKNOWN,
  PRIVATE_CLEAN,
  PRIVATE_DIRTY,
  ART,
  STACK,
  GRAPHICS,
  CODE,
  OTHERS
};
} // namespace anonymous

namespace profiler {

void MemoryLevelsSampler::GetProcessMemoryLevels(int pid) {
  char buffer[kBufferSize];
  char cmd[kCommandMaxLength];
  int num_written = snprintf(cmd, kCommandMaxLength, kDumpsysCommandFormat, pid);
  if (num_written >= kCommandMaxLength) {
    return; // TODO error handling.
  }

  std::string output = "";
  std::unique_ptr<FILE, int(*)(FILE*)> mem_info_file(popen(cmd, "r"), pclose);
  if (!mem_info_file) {
    return; // TODO error handling.
  }

  // Skip lines until actual data. Note that before N, "--checkin" is not an official flag
  // so the arg parsing logic complains about invalid arguments.
  do {
    if (feof(mem_info_file.get())) {
      return; // TODO error handling.
    }
    fgets(buffer, kBufferSize, mem_info_file.get());

    // Skip ahead until the header, which is in the format of: "time, (uptime), (realtime)".
  } while(strncmp(buffer, "time,", 5) != 0);

  // Gather the remaining content which should be a comma-delimited string.
  while (!feof(mem_info_file.get()) && fgets(buffer, kBufferSize, mem_info_file.get()) != nullptr) {
    output += buffer;
  }

  proto::MemoryData_MemorySample sample;
  ParseMemoryLevels(output, &sample);
}

void MemoryLevelsSampler::ParseMemoryLevels(const std::string& memory_info_string,
                                              proto::MemoryData_MemorySample* sample) {
  std::unique_ptr<char> delimited_memory_info(strdup(memory_info_string.c_str()));
  char* temp_memory_info_ptr = delimited_memory_info.get();
  char* result;

  uint32_t java_private = 0, native_private = 0, stack = 0,
           graphics = 0, code = 0, other_private = 0;

  // Version check.
  int version = ParseInt(&temp_memory_info_ptr, ",");
  int regularStatsFieldCount = 4;
  int privateDirtyStartIndex = 30;    // index before the private dirty category begins.
  int privateCleanStartIndex = 34;    // index before the private clean category begins.
  int otherStatsFieldCount;
  int otherStatsStartIndex;
  if (version == 4) {
    // New categories (e.g. swappable memory) have been inserted before the other stats categories
    // compared to version 3, so we only have to move forward the otherStatsStartIndex.
    otherStatsStartIndex = 47;
    otherStatsFieldCount = 8;
  } else if (version == 3) {
    otherStatsStartIndex = 39;
    otherStatsFieldCount = 6;
  } else {
    // Older versions predating Kitkat are unsupported - early return.
    return;
  }

  // The logic below extracts the private clean+dirty memory from the comma-delimited string,
  // which starts with: (the capitalized fields above are the ones we need)
  //   {version (parsed above), pid, process_name,}
  // then in groups of 4, the main heap info: (e.g. pss, shared dirty/clean, private dirty/clean)
  //    {NATIVE, DALVIK, other, total,}
  // follow by the other stats, in groups of the number defined in otherStatsFieldCount:
  //    {stats_label, total_pss, swappable_pss, shared_dirty, shared_clean, PRIVATE_DIRTY,
  //     PRIVATE_CLEAN,...}
  //
  // Note that the total private memory from this format is slightly less than the human-readable
  // dumpsys meminfo version, as that accounts for a small amount of "unknown" memory where the
  // "--checkin" version does not.
  int currentIndex = 0;
  while (true) {
    result = strsep(&temp_memory_info_ptr, ",");
    currentIndex++;
    if (result == nullptr) {
      // Reached the end of the output.
      break;
    }

    int memory_type = UNKNOWN;
    if (currentIndex >= otherStatsStartIndex) {
      if (strcmp(result, "Dalvik Other") == 0 ||
                 strcmp(result, "Ashmem") == 0 ||
                 strcmp(result, "Cursor") == 0 ||
                 strcmp(result, "Other dev") == 0 ||
                 strcmp(result, "Other mmap") == 0 ||
                 strcmp(result, "Other mtrack") == 0 ||
                 strcmp(result, "Unknown") == 0) {
        memory_type = OTHERS;
      } else if (strcmp(result, "Stack") == 0) {
        memory_type = STACK;
      } else if (strcmp(result, ".art mmap") == 0) {
        memory_type = ART;
      } else if (strcmp(result, "Gfx dev") == 0 ||
                 strcmp(result, "EGL mtrack") == 0 ||
                 strcmp(result, "GL mtrack") == 0) {
        memory_type = GRAPHICS;
      } else if (strcmp(result, ".so mmap") == 0 ||
                 strcmp(result, ".jar mmap") == 0 ||
                 strcmp(result, ".apk mmap") == 0 ||
                 strcmp(result, ".ttf mmap") == 0 ||
                 strcmp(result, ".dex mmap") == 0 ||
                 strcmp(result, ".oat mmap") == 0) {
        memory_type = CODE;
      }
    } else if (currentIndex == privateCleanStartIndex) {
      memory_type = PRIVATE_CLEAN;
    } else if (currentIndex == privateDirtyStartIndex) {
      memory_type = PRIVATE_DIRTY;
    }

    if (memory_type == PRIVATE_CLEAN) {
      other_private += ParseInt(&temp_memory_info_ptr, ","); // native private clean.
      other_private += ParseInt(&temp_memory_info_ptr, ","); // dalvik private clean.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - other private clean total.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - total private clean.
      currentIndex += regularStatsFieldCount;
    } else if (memory_type == PRIVATE_DIRTY) {
      native_private += ParseInt(&temp_memory_info_ptr, ",");  // native private dirty.
      java_private += ParseInt(&temp_memory_info_ptr, ",");  // dalvik private dirty.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - other private dirty are tracked separately.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - total private dirty.
      currentIndex += regularStatsFieldCount;
    } else if (memory_type != UNKNOWN) {
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - total pss.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - pss clean.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - shared dirty.
      strsep(&temp_memory_info_ptr, ",");  // UNUSED - shared clean.

      // Parse out private dirty and private clean.
      switch (memory_type) {
        case OTHERS:
          other_private += ParseInt(&temp_memory_info_ptr, ",");
          other_private += ParseInt(&temp_memory_info_ptr, ",");
          break;
        case STACK:
          stack += ParseInt(&temp_memory_info_ptr, ",");
          // Note that stack's private clean is treated as private others in dumpsys.
          other_private += ParseInt(&temp_memory_info_ptr, ",");
          break;
        case ART:
          java_private += ParseInt(&temp_memory_info_ptr, ",");
          java_private += ParseInt(&temp_memory_info_ptr, ",");
          break;
        case GRAPHICS:
          graphics += ParseInt(&temp_memory_info_ptr, ",");
          graphics += ParseInt(&temp_memory_info_ptr, ",");
          break;
        case CODE:
          code += ParseInt(&temp_memory_info_ptr, ",");
          code += ParseInt(&temp_memory_info_ptr, ",");
          break;
      }

      currentIndex += otherStatsFieldCount;
    }
  }

  sample->set_java_mem(java_private);
  sample->set_native_mem(native_private);
  sample->set_stack_mem(stack);
  sample->set_graphics_mem(graphics);
  sample->set_code_mem(code);
  sample->set_others_mem(other_private);
  sample->set_total_mem(java_private + native_private + stack + graphics + code + other_private);

  return;
}

int MemoryLevelsSampler::ParseInt(char** delimited_string, const char* delimiter) {
  char* result = strsep(delimited_string, delimiter);
  if (result == nullptr) {
    return 0;
  } else {
    return strtol(result, nullptr, 10);
  }
}

}  // namespace profiler