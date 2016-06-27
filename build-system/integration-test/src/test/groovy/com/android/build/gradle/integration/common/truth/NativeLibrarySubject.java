/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

import java.io.File;
import java.io.IOException;

/**
 * Truth subject for native libraries.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class NativeLibrarySubject extends Subject<NativeLibrarySubject, File> {

    public static final SubjectFactory<NativeLibrarySubject, File> FACTORY =
            new SubjectFactory<NativeLibrarySubject, File>() {
                @Override
                public NativeLibrarySubject getSubject(FailureStrategy fs, File that) {
                    return new NativeLibrarySubject(fs, that);
                }
            };

    public NativeLibrarySubject(FailureStrategy failureStrategy, File subject) {
        super(failureStrategy, subject);
    }

    /**
     * Call 'file' in shell to determine if libraries is stripped of debug symbols.
     */
    public void isStripped() throws IOException, InterruptedException {
        Process p = getFileData();
        if (p.exitValue() != 0) {
            String err = new String(ByteStreams.toByteArray(p.getErrorStream()), Charsets.UTF_8);
            failWithRawMessage("Error executing \'file\'.\n" + err);
        }
        String output = new String(ByteStreams.toByteArray(p.getInputStream()), Charsets.UTF_8);
        if (output.contains("not stripped")) {
            failWithRawMessage(
                    "Not true that <%s> is stripped.  File information:\n%s\n",
                    getDisplaySubject(),
                    output);
        }
    }

    public void isNotStripped() throws IOException, InterruptedException {
        Process p = getFileData();
        if (p.exitValue() != 0) {
            String err = new String(ByteStreams.toByteArray(p.getErrorStream()), Charsets.UTF_8);
            failWithRawMessage("Error executing \'file\'.\n" + err);
        }
        String output = new String(ByteStreams.toByteArray(p.getInputStream()), Charsets.UTF_8);
        if (!output.contains("not stripped")) {
            failWithRawMessage(
                    "Not true that <%s> is not stripped.  File information:\n%s\n",
                    getDisplaySubject(),
                    output);
        }
    }

    /**
     * Execute 'file' to obtain data about a file.
     */
    private Process getFileData() throws IOException, InterruptedException {
        Process p;
        p = Runtime.getRuntime().exec(new String[]{"file", getSubject().getAbsolutePath()});
        p.waitFor();
        return p;
    }
}
