/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.gradle.api.tasks.Input;

import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.Set;

/**
 * DSL object for configuring APK packaging options.
 *
 * <p>Packaging options are configured with three sets of paths: first-picks, merges and excludes:
 *
 * <dl>
 *     <dt>First-pick
 *     <dd>Paths that match a first-pick pattern will be selected into the APK. If more than one
 *     path matches the first-pick, only the first found will be selected.
 *
 *     <dt>Merge
 *     <dd>Paths that match a merge pattern will be concatenated and merged into the APK.
 *
 *     <dt>Exclude
 *     <dd>Paths that match an exclude pattern will not be included in the APK.
 * </dl>
 *
 * To decide the action on a specific path, the following algorithm is used:
 * <ol>
 *     <li>If any of the first-pick patterns match the path and that path has not been included in
 *     the APK, add it to the APK.
 *     <li>If any of the first-pick patterns match the path and that path has already been included
 *     in the APK, do not include the path in the APK.
 *     <li>If any of the merge patterns match the path and that path has not been included in the
 *     APK, add it to the APK.
 *     <li>If any of the merge patterns match the path and that path has already been included in
 *     the APK, concatenate the contents of the file to the ones already in the APK.
 *     <li>If any of the exclude patterns match the path, do not include it in the APK.
 *     <li>If none of the patterns above match the path and the path has not been included in the
 *     APK, add it to the APK.
 *     <li>Id none of the patterns above match the path and the path has been included in the APK,
 *     fail the build and signal a duplicate path error.
 * </ol>
 *
 * <p>Patterns in packaging options are specified as globs following the syntax in the
 * <a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-">
 * Java Filesystem API</a>. All paths should be configured using forward slashes ({@code /}).
 *
 * <p>All paths to be matched are provided as absolute paths from the root of the apk archive. So,
 * for example, {@code classes.dex} is matched as {@code /classes.dex}. This allows defining
 * patterns such as <code>&#042;&#042;/foo</code> to match the file {@code foo} in any directory,
 * including the root. Any pattern that does not start with a forward slash (or wildcard) is
 * automatically prepended with a forward slash. So, {@code file} and {@code /file} are effectively
 * the same pattern.
 *
 * <p>To decide the action on a specific path, the following algorithm is used:
 * <ol>
 *     <li>If any first-pick pattern matches the
 * </ol>
 *
 * <p>Several paths are excluded by default:
 * <ul>
 *     <li>{@code /META-INF/LICENCE}
 *     <li>{@code /META-INF/LICENCE.txt}
 *     <li>{@code /META-INF/NOTICE}
 *     <li>{@code /META-INF/NOTICE.txt}
 *     <li>{@code /LICENCE}
 *     <li>{@code /LICENCE.txt}
 *     <li>{@code /NOTICE}
 *     <li>{@code /NOTICE.txt}
 *     <li><code>&#042;&#042;/.svn/&#042;&#042;</code> (all {@code .svn} directory contents)
 *     <li><code>&#042;&#042;/CVS/&#042;&#042;</code> (all {@code CVS} directory contents)
 *     <li><code>&#042;&#042;/SCCS/&#042;&#042;</code> (all {@code SCCS} directory contents)
 *     <li><code>&#042;&#042;/.&#042;</code> (all UNIX hidden files)
 *     <li><code>&#042;&#042;/.&#042;/&#042;&#042;</code> (all contents of UNIX hidden directories)
 *     <li><code>&#042;&#042;/&#042;~</code> (temporary files)
 *     <li><code>&#042;&#042;/thumbs.db</code>
 *     <li><code>&#042;&#042;/picasa.ini</code>
 *     <li><code>&#042;&#042;/about.html</code>
 *     <li><code>&#042;&#042;/package.html</code>
 *     <li><code>&#042;&#042;/overview.html</code>
 *     <li><code>&#042;&#042;/_&#042;</code>
 *     <li><code>&#042;&#042;/_&#042;/&#042;&#042;</code>
 * </ul>
 *
 * <p>Example that adds the first {@code anyFileWillDo} file found and ignores all the others and
 * that excludes anything inside a {@code secret-data} directory that exists in the root:
 * <pre>
 * packagingOptions {
 *     pickFirst anyFileWillDo
 *     exclude /secret-data/&#042;&#042;
 * }
 * </pre>
 *
 * <p>Example that removes all patterns:
 * <pre>
 * packagingOptions {
 *     pickFirsts = [] // Not really needed because the default is empty.
 *     merges = []     // Not really needed because the default is empty.
 *     excludes = []
 * }
 * </pre>
 *
 * <p>Example that merges all {@code LICENCE.txt} files in the root.
 * <pre>
 * packagingOptions {
 *     merges = "/LICENCE.txt"
 *     excludes -= ["/LICENCE.txt"] // Not needed because merges take precedence over excludes
 * }
 * </pre>
 */
public class PackagingOptions implements com.android.builder.model.PackagingOptions {

    private Set<String> excludes = Sets.newHashSet();
    private Set<String> pickFirsts = Sets.newHashSet();
    private Set<String> merges = Sets.newHashSet();

    /**
     * Cache with compiled patterns.
     */
    @NonNull
    private final Map<String, PathMatcher> compiledPatterns = Maps.newHashMap();

    public PackagingOptions() {
        exclude("/META-INF/LICENCE");
        exclude("/META-INF/LICENSE.txt");
        exclude("/META-INF/NOTICE");
        exclude("/META-INF/NOTICE.txt");
        exclude("/NOTICE");
        exclude("/NOTICE.txt");
        exclude("/LICENSE.txt");
        exclude("/LICENSE");


        // Exclude version control folders.
        exclude("**/.svn/**");
        exclude("**/CVS/**");
        exclude("**/SCCS/**");

        // Exclude hidden and backup files.
        exclude("**/.*/**");
        exclude("**/.*");
        exclude("**/*~");

        // Exclude index files
        exclude("**/thumbs.db");
        exclude("**/picasa.ini");

        // Exclude javadoc files
        exclude("**/about.html");
        exclude("**/package.html");
        exclude("**/overview.html");

        // Exclude stuff for unknown reasons
        exclude("**/_*");
        exclude("**/_*/**");
    }

    /**
     * Returns the list of excluded paths.
     */
    @Override
    @NonNull
    @Input
    public Set<String> getExcludes() {
        return Sets.newHashSet(excludes);
    }

    public void setExcludes(Set<String> excludes) {
        this.excludes = Sets.newHashSet(excludes);
    }

    /**
     * Adds an excluded pattern.
     *
     * @param pattern the pattern
     */
    public void exclude(String pattern) {
        excludes.add(pattern);
    }

    /**
     * Returns the list of patterns where the first occurrence is packaged in the APK.
     */
    @Override
    @NonNull
    @Input
    public Set<String> getPickFirsts() {
        return Sets.newHashSet(pickFirsts);
    }

    /**
     * Adds a first-pick pattern. First pick patterns do get packaged in the APK, but only the
     * first occurrence found gets packaged.
     *
     * @param pattern the path to add.
     */
    public void pickFirst(String pattern) {
        pickFirsts.add(pattern);
    }

    public void setPickFirsts(Set<String> pickFirsts) {
        this.pickFirsts = Sets.newHashSet(pickFirsts);
    }

    /**
     * Returns the list of patterns where all occurrences are concatenated and packaged in the APK.
     */
    @Override
    @NonNull
    @Input
    public Set<String> getMerges() {
        return Sets.newHashSet(merges);
    }

    public void setMerges(Set<String> merges) {
        this.merges = Sets.newHashSet(merges);
    }

    /**
     * Adds a merge pattern.
     *
     * @param pattern the pattern, as packaged in the APK
     */
    public void merge(String pattern) {
        merges.add(pattern);
    }
}
