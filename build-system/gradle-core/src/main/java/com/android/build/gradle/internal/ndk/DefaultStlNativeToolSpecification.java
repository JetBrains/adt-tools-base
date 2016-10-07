/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ndk;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiler flags to configure STL.
 */
public class DefaultStlNativeToolSpecification implements StlNativeToolSpecification {
    @NonNull
    private NdkInfo ndkInfo;
    private Stl stl;
    @NonNull
    private StlSpecification stlLibSpec;

    public DefaultStlNativeToolSpecification(
            @NonNull NdkInfo ndkInfo,
            @NonNull StlSpecification stlLibSpec,
            @NonNull Stl stl) {
        this.ndkInfo = ndkInfo;
        this.stl = stl;
        this.stlLibSpec = stlLibSpec;
    }

    @Override
    public Iterable<String> getCFlags() {
        return Collections.emptyList();
    }

    @Override
    public Iterable<String> getCppFlags() {

        List<String> cppFlags = Lists.newArrayList();

        if (stl.getName().equals("c++")) {
            cppFlags.add("-std=c++11");
        }
        for (File include : getIncludes()) {
            cppFlags.add("-I" + include);
        }
        return cppFlags;
    }

    @Override
    public Iterable<String> getLdFlags() {
        List<String> flags = Lists.newArrayList();
        getSharedLibs().stream().forEach(lib -> addLib(flags, lib));
        getStaticLibs().stream().forEach(lib -> addLib(flags, lib));
        return flags;
    }

    private static void addLib(List<String> flags, File lib) {
        // Add folder containing the STL library to ld library path so that user can easily
        // append STL with the -l flag.
        flags.add("-L" + lib.getParent());
        flags.add("-l" + lib.getName().substring(3, lib.getName().lastIndexOf('.')));
    }


    @Override
    @NonNull
    public List<File> getIncludes() {
        return convertToFiles(stlLibSpec.getIncludes());
    }

    @Override
    @NonNull
    public List<File> getStaticLibs() {
        return convertToFiles(stlLibSpec.getStaticLibs());
    }

    @NonNull
    @Override
    public List<File> getSharedLibs() {
        return convertToFiles(stlLibSpec.getSharedLibs());
    }

    private List<File> convertToFiles(Collection<String> paths) {
        return paths.stream()
                .map(path -> new File(ndkInfo.getRootDirectory(), path))
                .collect(Collectors.toList());
    }
}
