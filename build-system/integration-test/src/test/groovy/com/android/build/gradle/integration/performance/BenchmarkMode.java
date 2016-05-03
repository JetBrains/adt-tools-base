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

package com.android.build.gradle.integration.performance;

public enum BenchmarkMode {
    EVALUATION, SYNC, BUILD_FULL, BUILD_INC_JAVA, BUILD_INC_RES_EDIT, BUILD_INC_RES_ADD,
    INSTANT_RUN_FULL_BUILD, INSTANT_RUN_BUILD_INC_JAVA, INSTANT_RUN_COLD_SWAP, INSTANT_RUN_HOT_SWAP,

}
