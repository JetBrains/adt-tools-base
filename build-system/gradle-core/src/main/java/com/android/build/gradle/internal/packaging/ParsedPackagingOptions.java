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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Maintains a set of parsed packaging options. Packaging options are defined in
 * {@link PackagingOptions}. This class extends the information in the packaging options by
 * compiling patterns and providing matching data.
 */
public class ParsedPackagingOptions {

    /**
     * Paths excluded.
     */
    @NonNull
    private final ImmutableSet<PathMatcher> excludes;

    /**
     * Paths that should do first-pick.
     */
    @NonNull
    private final ImmutableSet<PathMatcher> pickFirsts;

    /**
     * Paths that should be merged.
     */
    @NonNull
    private final ImmutableSet<PathMatcher> merges;

    /**
     * Exclude patterns.
     */
    @NonNull
    private final ImmutableSet<String> excludePatterns;

    /**
     * Pick-first patterns.
     */
    @NonNull
    private final ImmutableSet<String> pickFirstPatterns;

    /**
     * Merge patterns.
     */
    @NonNull
    private final ImmutableSet<String> mergePatterns;

    /**
     * Creates a new parsed packaging options based on the provided options.
     *
     * @param packagingOptions the DSL packaging options, if {@code null}, it is interpreted as
     * an empty packaging options
     */
    public ParsedPackagingOptions(@Nullable PackagingOptions packagingOptions) {
        if (packagingOptions == null) {
            packagingOptions = new PackagingOptions();
        }

        excludePatterns = ImmutableSet.copyOf(packagingOptions.getExcludes());
        pickFirstPatterns = ImmutableSet.copyOf(packagingOptions.getPickFirsts());
        mergePatterns = ImmutableSet.copyOf(packagingOptions.getMerges());

        excludes = ImmutableSet.copyOf(
                packagingOptions.getExcludes().stream()
                        .map(ParsedPackagingOptions::compileGlob)
                        .collect(Collectors.toSet()));

        pickFirsts = ImmutableSet.copyOf(
                packagingOptions.getPickFirsts().stream()
                        .map(ParsedPackagingOptions::compileGlob)
                        .collect(Collectors.toSet()));

        merges = ImmutableSet.copyOf(
                packagingOptions.getMerges().stream()
                        .map(ParsedPackagingOptions::compileGlob)
                        .collect(Collectors.toSet()));
    }

    /**
     * Compiles a glob pattern.
     *
     * @param pattern the pattern
     * @return the matcher
     */
    @NonNull
    private static PathMatcher compileGlob(@NonNull String pattern) {
        FileSystem fs = FileSystems.getDefault();

        if (!pattern.startsWith("/") && !pattern.startsWith("*")) {
            pattern = "/" + pattern;
        }

        return fs.getPathMatcher("glob:" + pattern);
    }

    /**
     * Obtains the action to perform for a path.
     *
     * @param archivePath the path
     * @return the packaging action
     */
    @NonNull
    public PackagingFileAction getAction(@NonNull String archivePath) {
        String absPath = archivePath;
        if (!absPath.startsWith("/")) {
            absPath = "/" + absPath;
        }

        Path path = Paths.get(absPath.replace('/', File.separatorChar));

        if (pickFirsts.stream().anyMatch(m -> m.matches(path))) {
            return PackagingFileAction.PICK_FIRST;
        }

        if (merges.stream().anyMatch(m -> m.matches(path))) {
            return PackagingFileAction.MERGE;
        }

        if (excludes.stream().anyMatch(m -> m.matches(path))) {
            return PackagingFileAction.EXCLUDE;
        }

        return PackagingFileAction.NONE;
    }

    /**
     * Obtains the raw set of exclude patterns.
     *
     * @return the patterns
     */
    @NonNull
    public ImmutableSet<String> getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Obtains the raw set of pick first patterns.
     *
     * @return the patterns
     */
    @NonNull
    public ImmutableSet<String> getPickFirstPatterns() {
        return pickFirstPatterns;
    }

    /**
     * Obtains the raw set of merge patterns.
     *
     * @return the patterns
     */
    @NonNull
    public ImmutableSet<String> getMergePatterns() {
        return mergePatterns;
    }
}
