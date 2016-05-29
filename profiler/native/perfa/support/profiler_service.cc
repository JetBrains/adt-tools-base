/*
 * Copyright (C) 2009 The Android Open Source Project
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
 *
 */
#include <string.h>
#include <jni.h>

// TODO when compiling on host provide mock implementations for testing
#ifdef __ANDROID__
#include <android/log.h>
#endif

#define STUDIO_PROFILER "StudioProfiler"

extern "C" JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_ProfilerService_nativeInitialize( JNIEnv* env, jobject thiz )
{
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_VERBOSE, STUDIO_PROFILER, "Initializing advanced profiling.", 1);
#endif
}
