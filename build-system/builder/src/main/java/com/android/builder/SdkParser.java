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

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;

import java.io.File;
import java.util.List;

/**
 * A parser able to parse the SDK and return valuable information to the build system.
 *
 */
public interface SdkParser {

    /**
     * Inits the parser with a target hash string and a build tools FullRevision.
     *
     * Note that this may be called several times on the same object, though it will always
     * be with the same values. Extra calls can be ignored.
     *
     * @param target the target hash string.
     * @param buildToolRevision the build tools revision
     * @param logger a logger object.
     *
     * @throws IllegalStateException if the SDK cannot parsed.
     *
     * @see IAndroidTarget#hashString()
     */
    public void initParser(@NonNull String target,
                           @NonNull FullRevision buildToolRevision,
                           @NonNull ILogger logger);

    /**
     * Returns the compilation target
     * @return the target.
     *
     * @throws IllegalStateException if the sdk was not initialized.
     */
    @NonNull
    IAndroidTarget getTarget();

    /**
     * Returns the BuildToolInfo
     * @return the build tool info
     *
     * @throws IllegalStateException if the sdk was not initialized.
     */
    @NonNull
    BuildToolInfo getBuildTools();

    /**
      * Returns the location of the annotations jar for compilation targets that are <= 15.
      */
    @NonNull
    String getAnnotationsJar();

    /**
     * Returns the revision of the installed platform tools component.
     *
     * @return the FullRevision or null if the revision couldn't not be found
     */
    @Nullable
    FullRevision getPlatformToolsRevision();

    /**
     * Returns the location of the zip align tool.
     */
    @NonNull
    File getZipAlign();

    /**
     * Returns the location of the adb tool.
     */
    @NonNull
    File getAdb();

    /**
     * Returns the location of artifact repositories built-in the SDK.
     * @return a non null list of repository folders.
     */
    @NonNull
    List<File> getRepositories();

    @Nullable
    File getNdkLocation();
}