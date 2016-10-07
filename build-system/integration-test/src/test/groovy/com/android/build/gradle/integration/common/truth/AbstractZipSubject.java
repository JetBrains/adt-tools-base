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
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
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
import java.util.zip.ZipException;
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

    @Nullable
    private ZipFile getZip() throws IOException {
        try {
            return new ZipFile(subject);
        } catch (ZipException e) {
            failWithRawMessage("Problem opening zip %s", subject);
            return null;
        }
    }

    /**
     * Asserts the zip file contains a file with the specified path.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void contains(@NonNull String path) throws IOException {
        ZipFile zip = getZip();
        if (zip == null) {
            return;
        }
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
        if (zip == null) {
            return;
        }
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
    public IterableSubject<? extends IterableSubject<?, String, List<String>>, String,
            List<String>> entries(
            @NonNull String conformingTo) throws IOException {

        ImmutableList.Builder<String> entries = ImmutableList.builder();
        Pattern pattern = Pattern.compile(conformingTo);
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipFileEntries.nextElement();
                if (pattern.matcher(zipEntry.getName()).matches()) {
                    entries.add(zipEntry.getName());
                }
            }
        }
        return check().<String, List<String>>that(entries.build());
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

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsFileWithMatch(@NonNull String path, @NonNull String pattern) {
        check().that(extractContentAsString(path)).containsMatch(pattern);
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
    public void containsFileWithoutContent(@NonNull String path, @NonNull String sub) {
        byte[] contents = extractContentAsByte(path);
        if (contents == null) {
            failWithRawMessage("No entry with path " + path);
        }
        int index = Bytes.indexOf(contents, sub.getBytes());
        if (index != -1) {
            failWithRawMessage("Found byte sequence at " + index + " in class file " + path);
        }
    }

    protected String extractContentAsString(@NonNull String path) {
        try {
            try (ZipFile zip = new ZipFile(subject)) {
                try (InputStream stream = getInputStream(zip, path)) {
                    // standardize on \n no matter which OS wrote the file.
                    return Joiner.on('\n').join(
                            CharStreams.readLines(new InputStreamReader(stream, Charsets.UTF_8)));
                }
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
            try (ZipFile zip = new ZipFile(subject)) {
                try (InputStream stream = getInputStream(zip, path)) {
                    return ByteStreams.toByteArray(stream);
                }
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

    /**
     * Perform an action on a Zip file entry. The zip file entry is extracted from the zip file
     * and stored to a temporary file passed to the {@link #doOnZipEntry(File)}.
     * @param <T> the expected return typ
     */
    interface ZipEntryAction<T> {

        /**
         * Perform an action on zip entry extracted to a temporary file. The file will only be
         * valid during the execution of the method.
         * @param extractedEntry the extract zip entry as a {@link File}
         * @return a result or null if no result could be provided.
         * @throws ProcessException
         */
        @Nullable
        T doOnZipEntry(File extractedEntry) throws ProcessException;
    }

    /**
     * Convenience method to extract an entry from the current zip file, save it as temporary file
     * and run a {@link ZipEntryAction} on it.
     * @param path the entry name in the subject's zip.
     * @param action the action to run on the extracted entry.
     * @param <T> the expected result type
     * @return result or null if it could not produce one.
     */
    @Nullable
    protected <T> T extractEntryAndRunAction(String path, ZipEntryAction<T> action)
            throws IOException, ProcessException {
        try (ZipFile zipFile = new ZipFile(getSubject())) {
            InputStream classDexStream = getInputStream(zipFile, path);
            if (classDexStream == null) {
                throw new IOException(path + " entry not found !");
            }
            try {
                byte[] content = ByteStreams.toByteArray(classDexStream);
                // write into tmp file
                File dexFile = File.createTempFile("dex", "");
                try {
                    Files.write(content, dexFile);
                    return action.doOnZipEntry(dexFile);
                } finally {
                    dexFile.delete();
                }
            } finally {
                classDexStream.close();
            }

        }
    }
}
