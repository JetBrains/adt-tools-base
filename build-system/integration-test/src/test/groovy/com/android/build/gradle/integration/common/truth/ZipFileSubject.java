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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
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
            failWithRawMessage("IOException thrown when creating ZipFile: %s.", e.toString());
        }
    }

    /**
     * Asserts the zip file contains a file with the specified path.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void contains(String path) {
        if (zip.getEntry(path) == null) {
            failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
        }
    }

    /**
     * Asserts the zip file does not contains a file with the specified path.
     */
    public void doesNotContain(String path) {
        if (zip.getEntry(path) != null) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", zip.getName(), path);
        }
    }

    /**
     * Asserts the zip file contains a file with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(String path, String content) {
        assertThat(extractContentAsString(path).trim()).named(path).comparesEqualTo(content.trim());
    }

    /**
     * Asserts the zip file contains a file with the specified byte array content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(String path, byte[] content) {
        assertThat(extractContentAsByte(path)).named(path).isEqualTo(content);
    }

    private String extractContentAsString(String path) {
        InputStream stream = getInputStream(path);
        try {
            return new String(ByteStreams.toByteArray(stream), Charsets.UTF_8).trim();
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting zip: %s", e.toString());
            return null;
        }
    }

    private byte[] extractContentAsByte(String path) {
        InputStream stream = getInputStream(path);
        try {
            return ByteStreams.toByteArray(stream);
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting zip: %s", e.toString());
            return null;
        }
    }

    private InputStream getInputStream(String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
            return null;
        }

        if (entry.isDirectory()) {
            failWithRawMessage("Unable to compare content, '%s' is a directory.", path);
        }

        try {
            return zip.getInputStream(entry);
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting zip: %s", e.toString());
            return null;
        }
    }
}
