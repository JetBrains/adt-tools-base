/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of the AndroidSourceDirectorySet.
 */
public class DefaultAndroidSourceDirectorySet implements AndroidSourceDirectorySet {

    private final String name;
    private final FileResolver fileResolver;
    private List<Object> source = Lists.newArrayList();

    DefaultAndroidSourceDirectorySet(@NonNull String name, @NonNull FileResolver fileResolver) {
        this.name = name;
        this.fileResolver = fileResolver;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet srcDir(Object srcDir) {
        source.add(srcDir);
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet srcDirs(Object... srcDirs) {
        Collections.addAll(source, srcDirs);
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet setSrcDirs(Iterable<?> srcDirs) {
        source.clear();
        for (Object srcDir : srcDirs) {
            source.add(srcDir);
        }
        return this;
    }

    @Override
    @NonNull
    public Set<File> getSrcDirs() {
        return fileResolver.resolveFiles(source.toArray()).getFiles();
    }

    @Override
    @NonNull
    public String toString() {
        return source.toString();
    }
}
