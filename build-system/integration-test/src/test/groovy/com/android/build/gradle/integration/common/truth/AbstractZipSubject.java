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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.primitives.Bytes;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Truth support for zip files.
 */
public abstract class AbstractZipSubject<T extends Subject<T, File>> extends Subject<T, File> {
    private final File subject;
    public AbstractZipSubject(@NonNull FailureStrategy failureStrategy, @NonNull File subject) {
        super(failureStrategy, subject);
        this.subject = subject;
        new FileSubject(failureStrategy, subject).exists();
    }

    private ZipFile getZip() throws IOException {
        return new ZipFile(subject);
    }

    /**
     * Asserts the zip file contains a file with the specified path.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void contains(@NonNull String path) throws IOException {
        ZipFile zip = getZip();
        try {
            if (zip.getEntry(path) == null) {
                failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
            }
        } finally {
            zip.close();
        }
    }

    /**
     * Asserts the zip file does not contains a file with the specified path.
     */
    public void doesNotContain(@NonNull String path) throws IOException {
        ZipFile zip = getZip();
        try {
            if (zip.getEntry(path) != null) {
                failWithRawMessage("'%s' unexpectedly contains '%s'", zip.getName(), path);
            }
        } finally {
            zip.close();
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
        return check().that(entries.build());
    }

    /**
     * Asserts the zip file contains a file with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(@NonNull String path, @NonNull String content) {
        check().that(extractContentAsString(path).trim()).named(internalCustomName() + ": " + path).isEqualTo(
                content.trim());
    }

    /**
     * Asserts the zip file contains a file with the specified byte array content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithContent(@NonNull String path, @NonNull byte[] content) {
        check().that(extractContentAsByte(path)).named(internalCustomName() + ": " + path).isEqualTo(content);
    }

    /**
     * Asserts the zip file contains a file <b>without</b> the specified byte sequence
     * <b>anywhere</b> in the file
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithoutContent(@NonNull String path, @NonNull byte[] sub) {
        byte[] contents = extractContentAsByte(path);
        int index = Bytes.indexOf(contents, sub);
        if (index != -1) {
            failWithRawMessage("Found byte sequence at " + index + " in class file " + path);
        }
    }

    protected String extractContentAsString(@NonNull String path) {
        try {
            ZipFile zip = new ZipFile(subject);
            try {
                InputStream stream = getInputStream(zip, path);
                try {
                    // standardize on \n no matter which OS wrote the file.
                    return Joiner.on('\n').join(
                            CharStreams.readLines(new InputStreamReader(stream, Charsets.UTF_8)));
                } finally {
                    stream.close();
                }
            } finally {
                zip.close();
            }
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting %1$s from zip %2$s: %3$s",
                    subject.getAbsolutePath(),
                    e.toString());
            return null;
        }
    }

    @Nullable
    protected byte[] extractContentAsByte(@NonNull String path) {
        try {
            ZipFile zip = new ZipFile(subject);
            try {
                InputStream stream = getInputStream(zip, path);
                try {
                    return ByteStreams.toByteArray(stream);
                } finally {
                    stream.close();
                }
            } finally {
                zip.close();
            }
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting %1$s from zip %2$s: %3$s",
                    path,
                    subject.getAbsolutePath(),
                    e.toString());
            return null;
        }
    }

    protected InputStream getInputStream(@NonNull ZipFile zip, @NonNull String path) {
        try {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                failWithRawMessage("'%s' does not contain '%s'", zip.getName(), path);
                return null;
            }

            if (entry.isDirectory()) {
                failWithRawMessage("Unable to compare content, '%s' is a directory.", path);
            }
            return zip.getInputStream(entry);
        } catch (IOException e) {
            failWithRawMessage("IOException when extracting %1$s from zip %2$s: %3$s",
                    path,
                    zip.getName(),
                    e.toString());
            return null;
        }
    }
}
