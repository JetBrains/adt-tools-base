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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.io.Files;

import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import groovy.lang.Closure;

/**
 * Allows project files to be modified, but stores their original content, so it can be restored for
 * the next test.
 */
public class TemporaryProjectModification {

    private final GradleTestProject mTestProject;

    private final Map<String, String> mFileContentToRestore = new HashMap<String, String>();

    private TemporaryProjectModification(GradleTestProject testProject) {
        mTestProject = testProject;
    }

    /**
     * Runs a test that mutates the project in a reversible way, and returns the project to its
     * original state after the callback has been run.
     *
     * @param project The project to modify.
     * @param test    The test to run.
     * @throws InitializationError if the project modification infrastructure fails.
     * @throws Exception passed through if the test throws an exception.
     */
    public static void doTest(GradleTestProject project, ModifiedProjectTest test) throws
            Exception {
        TemporaryProjectModification modifiedProject = new TemporaryProjectModification(project);
        try {
            test.runTest(modifiedProject);
        } finally {
            modifiedProject.close();
        }
    }

    public interface ModifiedProjectTest {
        void runTest(TemporaryProjectModification modifiedProject) throws Exception;
    }

    public void replaceFile(
            @NonNull String relativePath,
            @NonNull final String content) throws InitializationError {
        modifyFile(relativePath, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return content;
            }
        });
    }

    public void replaceInFile(
            @NonNull String relativePath,
            @NonNull final String search,
            @NonNull final String replace) throws InitializationError {
        modifyFile(relativePath, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.replaceAll(search, replace);
            }
        });

    }

    public void modifyFile(
            @NonNull String relativePath,
            @NonNull Function<String, String> modification) throws InitializationError {
        File file = mTestProject.file(relativePath);

        String currentContent = null;
        try {
            currentContent = Files.toString(file, Charsets.UTF_8);
        } catch (IOException e) {
            throw new InitializationError(e);
        }

        // We can modify multiple times, but we only want to store the original.
        if (!mFileContentToRestore.containsKey(relativePath)) {
            mFileContentToRestore.put(relativePath, currentContent);
        }

        String newContent = modification.apply(currentContent);

        if (newContent == null) {
            assertTrue("File should have been deleted", file.delete());
        } else {
            try {
                Files.write(newContent, file, Charsets.UTF_8);
            } catch (IOException e) {
                throw new InitializationError(e);
            }
        }
    }

    /**
     * Returns the project back to its original state.
     */
    public void close() throws InitializationError {
        try {
            for (Map.Entry<String, String> entry : mFileContentToRestore.entrySet()) {
                Files.write(entry.getValue(), mTestProject.file(entry.getKey()), Charsets.UTF_8);
            }
        } catch (IOException e) {
            throw new InitializationError(e);
        }
        mFileContentToRestore.clear();
    }
}
