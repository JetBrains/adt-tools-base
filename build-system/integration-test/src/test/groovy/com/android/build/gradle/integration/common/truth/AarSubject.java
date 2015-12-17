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

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatZip;

import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Closer;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.SubjectFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Truth support for aar files.
 */
public class AarSubject extends AbstractAndroidSubject<AarSubject> {

    static class Factory extends SubjectFactory<AarSubject, File> {
        @NonNull
        public static Factory get() {
            return new Factory();
        }

        private Factory() {}

        @Override
        public AarSubject getSubject(
                @NonNull FailureStrategy failureStrategy,
                @NonNull File subject) {
            return new AarSubject(failureStrategy, subject);
        }
    }

    public AarSubject(@NonNull FailureStrategy failureStrategy, @NonNull File subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public StringSubject textSymbolFile() throws IOException {
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            InputStream stream = getInputStream(zipFile, "R.txt");

            InputStreamReader inputStreamReader = new InputStreamReader(stream, Charsets.UTF_8);
            try {
                return new StringSubject(failureStrategy, CharStreams.toString(inputStreamReader));
            } finally {
                inputStreamReader.close();
            }
        } finally {
            zipFile.close();
        }
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     * @param scope the scope in which to search for the class.
     */
    @Override
    protected boolean checkForClass(
            @NonNull String expectedClassName,
            @NonNull ClassFileScope scope)
            throws ProcessException, IOException {
        if (!expectedClassName.startsWith("L") || !expectedClassName.endsWith(";")) {
            throw new RuntimeException("class name must be in the format Lcom/foo/Main;");
        }

        // in case of an aar, we look in the zip file, so we convert the class name to a zip entry
        // path.
        expectedClassName = expectedClassName.substring(1, expectedClassName.length() - 1) + DOT_CLASS;

        switch (scope) {
            case MAIN:
                return searchForEntryinZip(expectedClassName, FN_CLASSES_JAR);
            case ALL:
                if (searchForEntryinZip(expectedClassName, FN_CLASSES_JAR)) {
                    return true;
                }
                // intended fall-through
            case SECONDARY:
                // get all the entries and search for local jars.
                ZipFile zipFile = new ZipFile(getSubject());
                try {
                    Enumeration<? extends ZipEntry> zipFileEntries = zipFile.entries();
                    while (zipFileEntries.hasMoreElements()) {
                        ZipEntry zipEntry = zipFileEntries.nextElement();
                        String path = zipEntry.getName();
                        if (path.startsWith("libs/") && path.endsWith(DOT_JAR)) {
                            if (searchForEntryinZip(expectedClassName, path)) {
                                return true;
                            }
                        }
                    }
                } finally {
                    zipFile.close();
                }

                break;
        }

        return false;
    }

    private boolean searchForEntryinZip(
            @NonNull String entryName,
            @NonNull String zipPath) throws IOException {
        Closer closer = Closer.create();
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            InputStream stream = closer.register(getInputStream(zipFile, zipPath));
            ZipInputStream zis = closer.register(new ZipInputStream(stream));
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (entryName.equals(zipEntry.getName())) {
                    return true;
                }
            }

            // didn't find the class.
            return false;
        } finally {
            closer.close();
            zipFile.close();
        }
    }

    @Override
    protected boolean checkForJavaResource(@NonNull String resourcePath)
            throws ProcessException, IOException {
        return searchForEntryinZip(resourcePath, FN_CLASSES_JAR);
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @Override
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull String content)
            throws IOException, ProcessException {
        if (checkForJavaResource(path)) {
            // extract the jar file.
            File classesJar = extractClassesJar();

            // now check
            assertThatZip(classesJar).named("<" + getSubject().getName() + "/classes.jar>")
                    .containsFileWithContent(path, content);

            FileUtils.delete(classesJar);
        } else {
            failWithRawMessage("'%s' does not contains java resource '%s'", getSubject(), path);
        }
    }

    /**
     * Asserts the subject contains a java resources at the given path with the specified
     * byte array content.
     */
    @Override
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsJavaResourceWithContent(@NonNull String path, @NonNull byte[] content)
            throws IOException, ProcessException {
        if (checkForJavaResource(path)) {
            // extract the jar file.
            File classesJar = extractClassesJar();

            // now check
            assertThatZip(classesJar).named("<" + getSubject().getName() + "/classes.jar>")
                    .containsFileWithContent(path, content);

            FileUtils.delete(classesJar);
        } else {
            failWithRawMessage("'%s' does not contains java resource '%s'", getSubject(), path);
        }
    }

    @NonNull
    private File extractClassesJar() throws IOException {
        File classesJar = File.createTempFile("classes", ".jar");

        Closer closer = Closer.create();
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            InputStream stream = closer.register(getInputStream(zipFile, FN_CLASSES_JAR));
            ByteStreams.copy(stream, closer.register(new FileOutputStream(classesJar)));

            return classesJar;
        } finally {
            closer.close();
            zipFile.close();
        }
    }
}
