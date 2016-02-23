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

import static com.google.common.base.Preconditions.checkArgument;

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
     * Scope in which to search for classes.
     */
    public enum ClassFileScope {
        /**
         * The main class file. classes.dex for APK, and classes.jar for AAR
         */
        MAIN,
        /**
         * The secondary class files.
         * For APK: classes2.dex, classes3.dex, etc...
         * For AAR: local jars packaged under libs/
         */
        SECONDARY,
        /**
         * Main and secondary class files.
         */
        ALL,

        /**
         * InstantRun type of packaging, where some classes can be in the main or secondary class
         * files as well as in any dex file contained in an instant-run.zip file located in the
         * APK root.
         */
        INSTANT_RUN
    }

    /**
     * Returns true if the provided class is present in the file.
     * @param expectedClassName the class name in the format Lpkg1/pk2/Name;
     * @param scope the scope in which to search for the class.
     */
    protected abstract boolean checkForClass(
            @NonNull String expectedClassName,
            @NonNull ClassFileScope scope)
            throws ProcessException, IOException;

    protected abstract boolean checkForJavaResource(
            @NonNull String resourcePath)
            throws ProcessException, IOException;

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsClass(@NonNull String className) throws IOException, ProcessException {
        containsClass(className, ClassFileScope.ALL);
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsClass(@NonNull String className, @NonNull ClassFileScope scope)
            throws IOException, ProcessException {
        if (!checkForClass(className, scope)) {
            failWithRawMessage("'%s' does not contain '%s'", getDisplaySubject(), className);
        }
    }

    @Override
    public void contains(@NonNull String path) throws IOException {
        checkArgument(
                !path.startsWith("L") || !path.endsWith(";"),
                "Use containsClass to check for classes.");
        super.contains(path);
    }

    @Override
    public void doesNotContain(@NonNull String path) throws IOException {
        checkArgument(
                !path.startsWith("L") || !path.endsWith(";"),
                "Use doesNotContainClass to check for classes.");
        super.doesNotContain(path);
    }

    public void doesNotContainClass(@NonNull String className)
            throws IOException, ProcessException {
        doesNotContainClass(className, ClassFileScope.ALL);
    }

    public void doesNotContainClass(@NonNull String className, @NonNull ClassFileScope scope)
            throws IOException, ProcessException {
        if (checkForClass(className, scope)) {
            failWithRawMessage("'%s' unexpectedly contains '%s'", getDisplaySubject(), className);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsResource(@NonNull String name) throws IOException, ProcessException {
        if (!checkForResource(name)) {
            failWithRawMessage("'%s' does not contain resource '%s'", getDisplaySubject(), name);
        }
    }

    public void doesNotContainResource(@NonNull String name) throws IOException, ProcessException {
        if (checkForResource(name)) {
            failWithRawMessage("'%s' unexpectedly contains resource '%s'",
                    getDisplaySubject(), name);
        }
    }

    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public void containsJavaResource(@NonNull String name) throws IOException, ProcessException {
        if (!checkForJavaResource(name)) {
            failWithRawMessage("'%s' does not contain Java resource '%s'", getDisplaySubject(), name);
        }
    }

    public void doesNotContainJavaResource(@NonNull String name) throws IOException, ProcessException {
        if (checkForJavaResource(name)) {
            failWithRawMessage("'%s' unexpectedly contains Java resource '%s'",
                    getDisplaySubject(), name);
        }
    }

    /**
     * Asserts the subject contains a java resource at the given path with the specified String content.
     *
     * Content is trimmed when compared.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public abstract void containsJavaResourceWithContent(
            @NonNull String path, @NonNull String content) throws IOException, ProcessException;

    /**
     * Asserts the subject contains a java resource at the given path with the specified
     * byte array content.
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    public abstract void containsJavaResourceWithContent(
            @NonNull String path, @NonNull byte[] content) throws IOException, ProcessException;


    protected IndirectSubject<DexFileSubject> getDexFile(final File extractedDexFile) {
        return new IndirectSubject<DexFileSubject>() {
            @Override
            @NonNull
            public DexFileSubject that() {
                return DexFileSubject.FACTORY.getSubject(failureStrategy, extractedDexFile);
            }
        };
    }

    @Override
    protected String getDisplaySubject() {
        String name = (internalCustomName() == null) ? "" : "\"" + internalCustomName() + "\" ";
        return name + "<" + getSubject().getName() + ">";
    }

    private boolean checkForResource(String name) throws IOException {
        ZipFile zipFile = new ZipFile(getSubject());
        try {
            return zipFile.getEntry("res/" + name) != null;
        } finally {
            zipFile.close();
        }
    }
}
