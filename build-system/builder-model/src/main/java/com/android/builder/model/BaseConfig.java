/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Base config object for Build Type and Product flavor.
 */
public interface BaseConfig {

    @NonNull
    String getName();

    /**
     * Map of Build Config Fields where the key is the field name.
     *
     * @return a non-null map of class fields (possibly empty).
     */
    @NonNull
    Map<String, ClassField> getBuildConfigFields();

    /**
     * Returns the list of proguard rule files.
     *
     * @return a non-null list of files.
     */
    @NonNull
    Collection<File> getProguardFiles();

    /**
     * Returns the list of proguard rule files for consumers of the library to use.
     *
     * @return a non-null list of files.
     */
    @NonNull
    Collection<File> getConsumerProguardFiles();
}
