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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Truth support for zip files.
 */
public abstract class AbstractZipSubject<T extends Subject<T, File>> extends Subject<T, File> {
    private ZipFile zip;

    public AbstractZipSubject(@NonNull FailureStrategy failureStrategy, @NonNull File subject) {
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
    public void contains(@NonNull String path) {
        if (zip.getEntry(path) == null) {
            failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
        }
    }

    /**
     * Asserts the zip file does not contains a file with the specified path.
     */
    public void doesNotContain(@NonNull String path) {
        if (zip.getEntry(path) != null) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", zip.getName(), path);
        }
    }

    /**
     * Returns a {@link IterableSubject} of all the Zip entries which name matches the passed
     * regular expression.
     *
     * @param conformingTo a regular expression to match entries we are interested in.
     * @return a {@link IterableSubject} propositions for matching entries.
     * @throws IOException of the zip file cannot be opened.
     */
    public IterableSubject<? extends IterableSubject<?, String, List<String>>, String, List<String>> entries(
            @NonNull String conformingTo) throws IOException {

        ImmutableList.Builder<String> entries = ImmutableList.builder();
        Pattern pattern = Pattern.compile(conformingTo);
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipFileEntries.nextElement();
                if (pattern.matcher(zipEntry.getName()).matches()) {
                    entries.add(zipEntry.getName());
                }
            }
        } finally {
            zipFile.close();
        }
        return assertThat(entries.build());
    }

    /**
     * Asserts the zip file contains a file with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(@NonNull String path, @NonNull String content) {
        assertThat(extractContentAsString(path).trim()).named(path).comparesEqualTo(content.trim());
    }

    /**
     * Asserts the zip file contains a file with the specified byte array content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(@NonNull String path, @NonNull byte[] content) {
        assertThat(extractContentAsByte(path)).named(path).isEqualTo(content);
    }

    protected String extractContentAsString(@NonNull String path) {
        InputStream stream = getInputStream(path);
        try {
            return new String(ByteStreams.toByteArray(stream), Charsets.UTF_8).trim();
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting zip: %s", e.toString());
            return null;
        }
    }

    protected byte[] extractContentAsByte(@NonNull String path) {
        InputStream stream = getInputStream(path);
        try {
            return ByteStreams.toByteArray(stream);
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting zip: %s", e.toString());
            return null;
        }
    }

    protected InputStream getInputStream(@NonNull String path) {
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
