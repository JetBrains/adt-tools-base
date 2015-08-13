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

package com.android.build.gradle.integration.common.truth;

import com.android.annotations.NonNull;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

import java.io.File;

/**
 * Truth support for zip files.
 */
public class ZipFileSubject extends AbstractZipSubject<ZipFileSubject> {

    static class Factory extends SubjectFactory<ZipFileSubject, File> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public ZipFileSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull File subject) {
            return new ZipFileSubject(failureStrategy, subject);
        }
    }

    public ZipFileSubject(
            @NonNull FailureStrategy failureStrategy,
            @NonNull File subject) {
        super(failureStrategy, subject);
    }
}
