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
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Truth support for aar files.
 */
public class AarSubject extends AbstractAndroidSubject<AarSubject> {

    public AarSubject(FailureStrategy failureStrategy, File subject) {
        super(failureStrategy, subject);
    }

    public StringSubject textSymbolFile() throws IOException {
        InputStream stream = getInputStream("R.txt");

        return new StringSubject(failureStrategy,
                CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8)));
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     */
    @Override
    protected boolean checkForClass(
            @NonNull String expectedClassName)
            throws ProcessException, IOException {
        InputStream stream = getInputStream("classes.jar");

        ZipInputStream zis = new ZipInputStream(stream);
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (expectedClassName.equals(zipEntry.getName())) {
                    return true;
                }
            }

            // didn't find the class.
            return false;
        } finally {
            zis.close();
        }
    }

}
