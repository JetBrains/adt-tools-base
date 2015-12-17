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
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.junit.runners.model.InitializationError;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Allows project files to be modified, but stores their original content, so it can be restored for
 * the next test.
 */
public class TemporaryProjectModification {

    /**
     * The type of file change event.
     */
    enum FileChangeType {
        CHANGED, ADDED, REMOVED
    }

    /**
     * A file change event and the associated original file content.
     */
    private static class FileEvent {
        private final FileChangeType type;
        private final String fileContent;

        private FileEvent(
                FileChangeType type, String fileContent) {
            this.type = type;
            this.fileContent = fileContent;
        }

        public FileChangeType getType() {
            return type;
        }

        public String getFileContent() {
            return fileContent;
        }

        /**
         * Creates a {@link FileChangeType#CHANGED} FileEvent with a given original file content.
         * @param content the original file content
         * @return a FileEvent instance
         */
        static FileEvent changed(@NonNull String content) {
            return new FileEvent(FileChangeType.CHANGED, content);
        }

        /**
         * Creates a {@link FileChangeType#REMOVED} FileEvent with a given original file content.
         * @param content the original file content
         * @return a FileEvent instance
         */
        static FileEvent removed(@NonNull String content) {
            return new FileEvent(FileChangeType.REMOVED, content);
        }

        /**
         * Creates a {@link FileChangeType#ADDED} FileEvent.
         * @return a FileEvent instance
         */
        static FileEvent added() {
            return new FileEvent(FileChangeType.ADDED, null);
        }
    }

    private final GradleTestProject mTestProject;

    /**
     * Map of file change event. Key is relative path, value is the change event data
     */
    private final Map<String, FileEvent> mFileEvents = Maps.newHashMap();

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
            @NonNull final String content) throws IOException {
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
            @NonNull final String replace) throws IOException {
        modifyFile(relativePath, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.replaceAll(search, replace);
            }
        });

    }

    public void removeFile(@NonNull String relativePath) throws IOException {
        File file = getFile(relativePath);

        String currentContent = Files.toString(file, Charsets.UTF_8);

        // We can modify multiple times, but we only want to store the original.
        if (!mFileEvents.containsKey(relativePath)) {
            mFileEvents.put(relativePath, FileEvent.removed(currentContent));
        }

        FileUtils.delete(file);
    }

    public void addFile(
            @NonNull String relativePath,
            @NonNull String content) throws IOException {
        File file = getFile(relativePath);

        if (file.exists()) {
            throw new RuntimeException("File already exists: " + file);
        }

        FileUtils.mkdirs(file.getParentFile());

        mFileEvents.put(relativePath, FileEvent.added());

        Files.write(content, file, Charsets.UTF_8);
    }

    public void modifyFile(
            @NonNull String relativePath,
            @NonNull Function<String, String> modification) throws IOException {
        File file = getFile(relativePath);

        String currentContent = Files.toString(file, Charsets.UTF_8);

        // We can modify multiple times, but we only want to store the original.
        if (!mFileEvents.containsKey(relativePath)) {
            mFileEvents.put(relativePath, FileEvent.changed(currentContent));
        }

        String newContent = modification.apply(currentContent);

        if (newContent == null) {
            assertTrue("File should have been deleted", file.delete());
        } else {
            Files.write(newContent, file, Charsets.UTF_8);
        }
    }

    /**
     * Returns the project back to its original state.
     */
    private void close() throws IOException {
        for (Map.Entry<String, FileEvent> entry : mFileEvents.entrySet()) {
            FileEvent fileEvent = entry.getValue();
            switch (fileEvent.getType()) {
                case REMOVED:
                case CHANGED:
                    Files.write(fileEvent.getFileContent(),
                            getFile(entry.getKey()), Charsets.UTF_8);
                    break;
                case ADDED:
                    // it's fine if the file was already removed somehow.
                    FileUtils.deleteIfExists(mTestProject.file(entry.getKey()));
            }
        }

        mFileEvents.clear();
    }

    private File getFile(@NonNull String relativePath) {
        return mTestProject.file(relativePath.replace('/', File.separatorChar));
    }
}
