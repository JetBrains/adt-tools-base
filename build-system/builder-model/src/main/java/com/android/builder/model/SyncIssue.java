/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.annotations.Nullable;

/**
 * Class representing a sync issue.
 * The goal is to make these issues not fail the sync but instead report them at the end
 * of a successful sync.
 */
public interface SyncIssue {
    int SEVERITY_WARNING = 1;
    int SEVERITY_ERROR = 2;

    // Generic error with no data payload, and no expected quick fix in IDE.
    int TYPE_GENERIC                     = 0;

    // data is expiration data
    int TYPE_PLUGIN_OBSOLETE          = 1;

    // data is dependency coordinate
    int TYPE_UNRESOLVED_DEPENDENCY    = 2;

    // data is dependency coordinate
    int TYPE_DEPENDENCY_IS_APK        = 3;

    // data is dependency coordinate
    int TYPE_DEPENDENCY_IS_APKLIB     = 4;

    // data is local file
    int TYPE_NON_JAR_LOCAL_DEP        = 5;

    // data is dependency coordinate/path
    int TYPE_NON_JAR_PACKAGE_DEP      = 6;

    // data is dependency coordinate/path
    int TYPE_NON_JAR_PROVIDED_DEP     = 7;

    // data is dependency coordinate/path
    int TYPE_JAR_DEPEND_ON_AAR        = 8;

    /**
     * Mismatch dependency version between tested and test
     * app. Data is dep coordinate without the version (groupId:artifactId)
     */
    int TYPE_MISMATCH_DEP             = 9;

    // data is dependency coordinate
    int TYPE_OPTIONAL_LIB_NOT_FOUND   = 10;

    // data is the component that does not support Jack. Data is variant name.
    int TYPE_JACK_IS_NOT_SUPPORTED    = 11;

    // data is the min version of Gradle
    int TYPE_GRADLE_TOO_OLD           = 12;

    // data is the required min build tools version, parsable by Revision
    int TYPE_BUILD_TOOLS_TOO_LOW      = 13;

    // found dependency that's the maven published android.jar
    // data is the maven artifact coordinates.
    int TYPE_DEPENDENCY_MAVEN_ANDROID = 14;

    // found dependency that is known to be inside android.jar
    // data is maven artifact coordinates.
    int TYPE_DEPENDENCY_INTERNAL_CONFLICT = 15;

    // Errors configuring NativeConfigValues for individual individual variants
    int TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION = 16;

    // Errors configuring NativeConfigValues. There was a process exception.
    // Data contains STDERR which should be interpreted by Android Studio
    int TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION = 17;

    // Cannot use Java 8 Language features without Jack.
    int TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES = 18;


    int TYPE_MAX                      = 18; // increment when adding new types.

    /**
     * Returns the severity of the issue.
     */
    int getSeverity();

    /**
     * Returns the type of the issue.
     */
    int getType();

    /**
     * Returns the data of the issue.
     *
     * This is a machine-readable string used by the IDE for known issue types.
     */
    @Nullable
    String getData();

    /**
     * Returns the a user-readable message for the issue.
     *
     * This is used by IDEs that do not recognize the issue type (ie older IDE released before
     * the type was added to the plugin).
     */
    @NonNull
    String getMessage();
}
