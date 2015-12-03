/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.fd.runtime;

import com.android.annotations.NonNull;

/**
 * Shared path-related logic between Android Studio and the Instant Run server.
 */
public final class Paths {

    public static final String DEX_DIRECTORY_NAME = "dex";

    @NonNull
    public static String getDataDirectory(@NonNull String applicationId) {
        return "/data/data/" + applicationId + "/files/studio-fd";
    }

    @NonNull
    public static String getDexFileDirectory(@NonNull String applicationId) {
        return getDataDirectory(applicationId) + "/" + DEX_DIRECTORY_NAME;
    }


}
