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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * Abstract class implementing AndroidTestApp.
 */
public abstract class AbstractAndroidTestApp implements AndroidTestApp {
    private Multimap<String, TestSourceFile> sourceFiles = ArrayListMultimap.create();

    protected void addFiles(TestSourceFile... files) {
        for (TestSourceFile file : files) {
            sourceFiles.put(file.getName(), file);
        }
    }

    @Override
    public TestSourceFile getFile(String filename) {
        Collection<TestSourceFile> files = sourceFiles.get(filename);
        if (files.isEmpty()) {
            throw new NoSuchElementException("Unable to file source file: " + filename + ".");
        } else if (files.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple source files named '" + filename + "'.  Specify the path to get one "
                            + "of the following files: \n"
                            + Joiner.on('\n').join(files));
        }
        return files.iterator().next();
    }

    @Override
    public TestSourceFile getFile(String filename, final String path) {
        Collection<TestSourceFile> files = sourceFiles.get(filename);
        return Iterables.find(files, new Predicate<TestSourceFile>() {
            @Override
            public boolean apply(TestSourceFile testSourceFile) {
                return path.equals(testSourceFile.getPath());
            }
        });
    }

    @Override
    public void addFile(TestSourceFile file) {
        sourceFiles.put(file.getName(), file);
    }

    @Override
    public boolean removeFile(TestSourceFile file) {
        return sourceFiles.remove(file.getName(), file);
    }

    @Override
    public Collection<TestSourceFile> getAllSourceFiles() {
        return sourceFiles.values();
    }

    @Override
    public void write(@NonNull File projectDir, @Nullable String buildScriptContent)
            throws IOException {
        for (TestSourceFile srcFile : getAllSourceFiles()) {
            srcFile.writeToDir(projectDir);
        }
    }

}
