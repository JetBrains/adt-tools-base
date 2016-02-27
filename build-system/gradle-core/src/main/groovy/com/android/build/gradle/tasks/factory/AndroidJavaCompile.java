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

package com.android.build.gradle.tasks.factory;

import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;

import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

/**
 * Specialization of the JavaCompile task to record execution time.
 */
public class AndroidJavaCompile extends JavaCompile {

    String compileSdkVersion;

    InstantRunBuildContext mBuildContext;

    @Override
    protected void compile(IncrementalTaskInputs inputs) {
        getLogger().info(
                "Compiling with source level {} and target level {}.",
                getSourceCompatibility(),
                getTargetCompatibility());
        if (isPostN()) {
            if (!JavaVersion.current().isJava8Compatible()) {
                throw new RuntimeException("compileSdkVersion '" + compileSdkVersion + "' requires "
                        + "JDK 1.8 or later to compile.");
            }
        }
        mBuildContext.startRecording(InstantRunBuildContext.TaskType.JAVAC);
        super.compile(inputs);
        mBuildContext.stopRecording(InstantRunBuildContext.TaskType.JAVAC);
    }

    private boolean isPostN() {
        final AndroidVersion hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        return hash != null && hash.getApiLevel() >= 24;
    }
}
