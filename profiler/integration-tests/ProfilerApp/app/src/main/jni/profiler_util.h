#ifndef PROFILER_UTIL_H_
#define PROFILER_UTIL_H_

#include <android/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "profiler", __VA_ARGS__)

#endif // PROFILER_UTIL_H_