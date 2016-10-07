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
import com.google.common.base.Joiner;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * Describes a source file for integration test.
 */
public class TestSourceFile {
    private final String parent;
    private final String name;
    private final byte[] content;

    public TestSourceFile(@NonNull String parent, @NonNull String name, @NonNull byte[] content) {
        this.parent = parent;
        this.name = name;
        this.content = content;
    }

    public TestSourceFile(@NonNull String parent, @NonNull String name, @NonNull String content) {
        this(parent, name, content.getBytes());
    }

    public String getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public File getFile() {
        return new File(parent, name);
    }

    public String getPath() {
        return getFile().getPath();
    }

    public byte[] getContent() {
        return content;
    }

    public File writeToDir(File base) throws IOException {
        File file = new File(base, Joiner.on(File.separatorChar).join(parent, name));
        writeToFile(file);
        return file;
    }

    public void writeToFile(File file) throws IOException {
        if (!file.exists()) {
            Files.createParentDirs(file);
        }
        Files.write(content, file);
    }
}

