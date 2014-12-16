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
import com.google.common.truth.Subject;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Truth support for zip files.
 */
public class ZipFileSubject extends Subject<ZipFileSubject, File> {
    private ZipFile zip;

    public ZipFileSubject(FailureStrategy failureStrategy, File subject) {
        super(failureStrategy, subject);
        try {
            zip = new ZipFile(subject);
        } catch (IOException e) {
            failWithRawMessage("IOException thrown when creating ZipFile.");
        }
    }

    public void contains(String path) {
        if (zip.getEntry(path) == null) {
            failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
        }
    }

    public void doesNotContain(String path) {
        if (zip.getEntry(path) != null) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", zip.getName(), path);
        }
    }
}
