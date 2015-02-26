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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

import java.io.File;

/**
 * Factory to add truth support for zip files.
 */
public class ZipFileSubjectFactory extends SubjectFactory<ZipFileSubject, File> {
    public static ZipFileSubjectFactory factory() {
        return new ZipFileSubjectFactory();
    }

    private ZipFileSubjectFactory() {}

    @Override
    public ZipFileSubject getSubject(FailureStrategy failureStrategy, File subject) {
        return new ZipFileSubject(failureStrategy, subject);
    }
}
