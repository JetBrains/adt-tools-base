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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.build.gradle.integration.common.fixture.TestProject;

import java.util.Collection;

/**
 * Interface for an single module Android test application.
 *
 * A test application is a collection of source code that may be reused for multiple tests.
 */
public interface AndroidTestApp extends TestProject {
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
}
