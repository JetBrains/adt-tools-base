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

package com.android.build.gradle.internal.gson;

import com.android.annotations.NonNull;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.io.IOException;

/**
 * Gson Type Adaptor for File.
 *
 * Using Gradle {@link FileResolver} to resolve relative paths.
 */
public class FileGsonTypeAdaptor extends TypeAdapter<File> {
    @NonNull
    private final FileResolver fileResolver;

    public FileGsonTypeAdaptor(@NonNull FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void write(JsonWriter jsonWriter, File file) throws IOException {
        jsonWriter.value(file.getPath());
    }

    @Override
    public File read(JsonReader jsonReader) throws IOException {
        String path = jsonReader.nextString();
        return fileResolver.resolve(path);
    }
}
