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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.truth.IncrementalFileSubject;
import com.android.build.gradle.integration.common.truth.IncrementalFileSubjectFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Class for parsing Gradle output for verifying incremental tasks.
 */
public class IncrementalTaskOutputVerifier {

    @NonNull
    final String gradleOutput;

    public IncrementalTaskOutputVerifier(@NonNull String gradleOutput) {
        this.gradleOutput = gradleOutput;
    }

    /**
     * Truth style assert for check changes to a file.
     */
    public IncrementalFileSubject assertThatFile(File subject) {
        return assert_().about(IncrementalFileSubjectFactory.factory(gradleOutput)).that(subject);
    }
}
