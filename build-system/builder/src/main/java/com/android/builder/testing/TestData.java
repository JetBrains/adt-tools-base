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
import com.android.builder.model.ApiVersion;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Data representing the test app and the tested application/library.
 */
public interface TestData {

    /**
     * Returns the application id.
     *
     * @return the id
     */
    @NonNull
    String getApplicationId();

    /**
     * Returns the tested application id. This can be empty if the test package is self-contained.
     *
     * @return the id or null.
     */
    @Nullable
    String getTestedApplicationId();

    @NonNull
    String getInstrumentationRunner();

    @NonNull
    Boolean getHandleProfiling();

    @NonNull
    Boolean getFunctionalTest();

    /**
     * Returns whether the tested app is enabled for code coverage
     */
    boolean isTestCoverageEnabled();

    /**
     * The min SDK version of the app
     */
    @NonNull
    ApiVersion getMinSdkVersion();

    boolean isLibrary();

    /**
     * Returns an APK file to install based on given density and abis.
     * @param density the density
     * @param language the device's language
     * @param region the device's region
     * @param abis a list of ABIs in descending priority order.
     * @return the file to install or null if non is compatible.
     */
    @NonNull
    ImmutableList<File> getTestedApks(
            int density,
            @Nullable String language,
            @Nullable String region,
            @NonNull List<String> abis);
}
