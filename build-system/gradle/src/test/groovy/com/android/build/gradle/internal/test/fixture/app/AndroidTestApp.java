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

package com.android.build.gradle.internal.test.fixture.app;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Interface for an Android test application.
 *
 * A test application is a collection of source code that may be reused for multiple tests.
 */
public interface AndroidTestApp {
    /**
     * Return a source file in the test app with the specified filename.
     */
    TestSourceFile getFile(String filename);

    /**
     * Return a source file in the test app matching the specified filename and path.
     */
    TestSourceFile getFile(String filename, String path);

    /**
     * Return all source files in this test app.
     */
    Collection<TestSourceFile> getAllSourceFiles();

    /**
     * Add an additional source file to the test app.
     */
    void addFile(TestSourceFile file);

    /**
     * Remove a source file from the test app.
     */
    boolean removeFile(TestSourceFile file);

    /**
     * Create all source files in the specified directory.
     *
     * @param sourceDir Directory to create the source files in.
     */
    void writeSources(File sourceDir) throws IOException;
}
