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

#include <jni.h>
#include <stddef.h>
#include <vector>

extern "C" {    // prevent C++ name mangling

static int* tempInts = NULL;            // churn pointer
static std::vector<int*> intVector;

void Java_com_android_profilerapp_memory_MemoryFragment_jniAllocIntArray(JNIEnv* env,
                                                     jobject jobj, jint count, jboolean initialize)
{
    int* stuff = (int*) malloc(sizeof(int) * count);
    if (initialize)
    {
        memset(stuff, 0, sizeof(int) * count);
    }

    intVector.push_back(stuff);
}

void Java_com_android_profilerapp_memory_MemoryFragment_jniFreeIntArrays(JNIEnv* env, jobject jobj)
{
    for (std::vector<int*>::iterator it = intVector.begin(); it != intVector.end(); ++it) {
        free(*it);
    }

    intVector.clear();
}

void Java_com_android_profilerapp_memory_MemoryFragment_jniLeakIntArrays(JNIEnv* env,
                                                                                  jobject jobj)
{
    intVector.clear();
}


void Java_com_android_profilerapp_memory_MemoryFragment_jniAllocTempIntArray(JNIEnv* env,
                                                     jobject jobj, jint count, jboolean initialize)
{
    if (tempInts != NULL)
    {
        free(tempInts); // free previous before we churn more
    }

    tempInts = (int*) malloc(sizeof(int) * count);
    if (initialize)
    {
        memset(tempInts, 0, sizeof(int) * count);
    }
}

void Java_com_android_profilerapp_memory_MemoryFragment_jniFreeTempIntArray(JNIEnv* env,
                                                                            jobject jobj)
{
    if (tempInts != NULL)
    {
        free(tempInts);
        tempInts = NULL;
    }
}

}