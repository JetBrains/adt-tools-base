#include "activity_manager.h"

using std::string;

namespace {
const char *const kAmExecutable = "/system/bin/am";
}

namespace profiler {

ActivityManager::ActivityManager() : BashCommandRunner(kAmExecutable) {}

bool ActivityManager::StartProfiling(const ProfilingMode profiling_mode,
                                     const string &app_package_name,
                                     string *error_string) const {
  if (profiling_mode != SAMPLING) {
    *error_string = "Only sampling profiler is currently supported";
    return false;
  }
  string parameters;
  parameters.append("profile start ");
  parameters.append(app_package_name);
  return Run(parameters, error_string);
}

bool ActivityManager::StopProfiling(const string &app_package_name,
                                    string *error_string) const {
  string parameters;
  parameters.append("profile stop ");
  parameters.append(app_package_name);
  return Run(parameters, error_string);
}
}  // namespace profiler
