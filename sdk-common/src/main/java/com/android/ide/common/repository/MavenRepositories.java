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

package com.android.ide.common.repository;

import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for dealing with the standard m2repository directory layout.
 */
@SuppressWarnings("WeakerAccess")
public class MavenRepositories {

    private static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";

    private MavenRepositories() {}

    /**
     * Find the best matching {@link GradleCoordinate}
     *
     * @param groupId      the artifact group id
     * @param artifactId   the artifact id
     * @param repository   the path to the m2repository directory
     * @param filter       an optional filter which the matched coordinate's version name must start
     *                     with
     * @param allowPreview whether preview versions are allowed to match
     * @return the best (highest version) matching coordinate, or null if none were found
     */
    @Nullable
    public static GradleCoordinate getHighestInstalledVersion(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull File repository,
            @Nullable String filter,
            boolean allowPreview,
            @NonNull FileOp fileOp) {
        File versionDir = getArtifactIdDirectory(repository, groupId, artifactId);
        File[] versions = fileOp.listFiles(versionDir);
        List<GradleCoordinate> versionCoordinates = Lists.newArrayList();
        for (File dir : versions) {
            if (!fileOp.isDirectory(dir)) {
                continue;
            }
            if (filter != null && !dir.getName().startsWith(filter)) {
                continue;
            }
            GradleCoordinate gc = GradleCoordinate.parseCoordinateString(
                    groupId + ":" + artifactId + ":" + dir.getName());

            if (gc != null && (allowPreview || !isPreview(gc))) {
                versionCoordinates.add(gc);
            }
        }

        if (!versionCoordinates.isEmpty()) {
            return Collections.max(versionCoordinates, COMPARE_PLUS_HIGHER);
        }

        return null;
    }

  /**
   * Decides whether a given {@link GradleCoordinate} is considered preview.
   *
   * <p>This is mostly compatible with {@link GradleCoordinate#isPreview()}, but there is one edge
   * case that we need to handle, related to Play Services. (See https://b.android.com/75292)
   */
    public static boolean isPreview(GradleCoordinate coordinate) {
        //noinspection SimplifiableIfStatement
        if (coordinate.isPreview()) {
            return true;
        }

        return "com.google.android.gms".equals(coordinate.getGroupId())
                && "play-services".equals(coordinate.getArtifactId())
                && "5.2.08".equals(coordinate.getRevision());
    }

    /**
     * @deprecated For testability, use {@link MavenRepositories#getHighestInstalledVersion(String,
     * String, File, String, boolean, FileOp)}.
     */
    @Deprecated
    @Nullable
    public static GradleCoordinate getHighestInstalledVersion(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull File repository,
            @Nullable String filter,
            boolean allowPreview) {
        return getHighestInstalledVersion(groupId, artifactId, repository, filter, allowPreview,
                FileOpUtils.create());
    }

    public static File getArtifactIdDirectory(
            @NonNull File repository,
            @NonNull String groupId,
            @NonNull String artifactId) {
        return new File(repository,
                groupId.replace('.', separatorChar) + separator + artifactId);
    }

    public static File getArtifactDirectory(
            @NonNull File repository,
            @NonNull GradleCoordinate coordinate) {
        checkNotNull(coordinate.getGroupId());
        checkNotNull(coordinate.getArtifactId());

        File artifactIdDirectory = getArtifactIdDirectory(
                repository, coordinate.getGroupId(), coordinate.getArtifactId());

        return new File(artifactIdDirectory, coordinate.getRevision());
    }

    public static File getMavenMetadataFile(
            @NonNull File repository,
            @NonNull String groupId,
            @NonNull String artifactId) {
        return new File(getArtifactIdDirectory(repository, groupId, artifactId),
                MAVEN_METADATA_FILE_NAME);
    }
}
