/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.Set;

/**
 */
public interface TestData {

    /**
     * Returns the package name.
     *
     * @return the package name
     */
    @NonNull
    String getPackageName();

    /**
     * Returns the tested package name. This can be empty if the test package is self-contained.
     *
     * @return the package name or null.
     */
    @Nullable
    String getTestedPackageName();

    @NonNull
    String getInstrumentationRunner();

    @NonNull
    Boolean getHandleProfiling();

    @NonNull
    Boolean getFunctionalTest();

    int getMinSdkVersion();

    /**
     * List of supported ABIs. Null means all.
     * @return a list of abi or null for all
     */
    @Nullable
    Set<String> getSupportedAbis();
}
