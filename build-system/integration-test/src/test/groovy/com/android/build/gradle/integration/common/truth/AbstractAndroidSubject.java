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
import com.google.common.truth.FailureStrategy;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Base Truth support for android archives (aar and apk)
 */
public abstract class AbstractAndroidSubject<T extends AbstractZipSubject<T>> extends AbstractZipSubject<T> {

    public AbstractAndroidSubject(@NonNull FailureStrategy failureStrategy, @NonNull File subject) {
        super(failureStrategy, subject);
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     */
    protected abstract boolean checkForClass(
            @NonNull String expectedClassName)
            throws ProcessException, IOException;

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsClass(String className) throws IOException, ProcessException {
        if (!checkForClass(className)) {
            failWithRawMessage("'%s' does not contain '%s'", getDisplaySubject(), className);
        }
    }

    public void doesNotContainClass(String className) throws IOException, ProcessException {
        if (checkForClass(className)) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", getDisplaySubject(), className);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsResource(String name) throws IOException, ProcessException {
        if (!checkForResource(name)) {
            failWithRawMessage("'%s' does not contain resource '%s'", getDisplaySubject(), name);
        }
    }

    public void doesNotContainResource(String name) throws IOException, ProcessException {
        if (checkForResource(name)) {
            failWithRawMessage("'%s' unexpectedly contains resource '%s'", getDisplaySubject(), name);
        }
    }

    @Override
    protected String getDisplaySubject() {
        String name = (internalCustomName() == null) ? "" : "\"" + internalCustomName() + "\" ";
        return name + "<" + getSubject().getName() + ">";
    }

    private boolean checkForResource(String name) throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(getSubject());
            return zipFile.getEntry("res/" + name) != null;
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }
}
