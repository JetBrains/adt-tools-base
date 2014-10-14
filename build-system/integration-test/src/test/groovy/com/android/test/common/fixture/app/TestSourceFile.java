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

package com.android.test.common.fixture.app;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * Describes a source file for integration test.
 */
public class TestSourceFile {
    private final String path;
    private final String name;
    private final String content;

    public TestSourceFile(String path, String name, String content) {
        this.path = path;
        this.name = name;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public File writeToDir(File base) throws IOException {
        File file = new File(base, Joiner.on(File.separatorChar).join(path, name));
        writeToFile(file);
        return file;
    }

    public void writeToFile(File file) throws IOException {
        if (!file.exists()) {
            Files.createParentDirs(file);
        }
        Files.append(content, file, Charsets.UTF_8);
    }
}

