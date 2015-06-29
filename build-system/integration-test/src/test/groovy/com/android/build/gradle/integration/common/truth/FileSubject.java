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

package com.android.build.gradle.integration.common.truth;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.io.File;

/**
 * Truth support for validating File.
 */
public class FileSubject extends Subject<FileSubject, File> {
    public FileSubject(FailureStrategy failureStrategy, File subject) {
        super(failureStrategy, subject);
    }

    public void exists() {
        if (!getSubject().exists()) {
            fail("exists");
        }
    }

    public void doesNotExist() {
        if (getSubject().exists()) {
            fail("does not exist");
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isFile() {
        if (!getSubject().exists()) {
            fail("is a file");
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void isDirectory() {
        if (!getSubject().isDirectory()) {
            fail("is a directory");
        }
    }

    public FileSubject subFile(String path) {
        if (!getSubject().isDirectory()) {
            fail("is a directory");
        }
        return new FileSubject(failureStrategy, new File(getSubject(), path));
    }
}
