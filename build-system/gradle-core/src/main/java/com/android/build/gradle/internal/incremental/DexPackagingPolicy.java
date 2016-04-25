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

package com.android.build.gradle.internal.incremental;

import com.android.build.gradle.internal.transforms.InstantRunSlicer;

/**
 * Created by jedo on 4/18/16.
 */
public enum DexPackagingPolicy {
    /**
     * Standard Dex packaging policy, all dex files will be packaged at the root of the APK.
     */
    STANDARD,

    /**
     * InstantRun specific Dex packaging policy, all dex files with a name containing {@link
     * InstantRunSlicer#MAIN_SLICE_NAME} will be packaged at the root of the APK while all other
     * dex files will be packaged in a instant-run.zip itself packaged at the root of the APK.
     */
    INSTANT_RUN_SHARDS_IN_SINGLE_APK,

    /**
     * InstantRun specific packaging based on split APKs. Each dex files with a name
     * containing {@link InstantRunSlicer#MAIN_SLICE_NAME} will be packaged normally as a dex file
     * at the root of the APK while each other one will be packaged as a separate split APK.
     */
    INSTANT_RUN_MULTI_APK
}
