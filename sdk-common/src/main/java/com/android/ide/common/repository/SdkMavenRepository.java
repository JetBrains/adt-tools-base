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

package com.android.ide.common.repository;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * A {@linkplain com.android.ide.common.repository.SdkMavenRepository} represents a Maven
 * repository that is shipped with the SDK and located in the {@code extras} folder of the
 * SDK location.
 */
public enum SdkMavenRepository {
    /** The Android repository; contains support lib, app compat, media router, etc */
    ANDROID("android", "Android Support Repository"),

    /** The Google repository; contains Play Services etc */
    GOOGLE("google", "Google Support Repository");

    @NonNull private final String mDir;
    @NonNull private final String myDisplayName;

    SdkMavenRepository(@NonNull String dir, @NonNull String displayName) {
        mDir = dir;
        myDisplayName = displayName;
    }

    /**
     * @deprecated For testability, use {@link #getRepositoryLocation(File, boolean, FileOp)}.
     */
    @Deprecated
    @Nullable
    public File getRepositoryLocation(@Nullable File sdkHome, boolean requireExists) {
        return getRepositoryLocation(sdkHome, requireExists, FileOpUtils.create());
    }

    /**
     * Returns the location of the repository within a given SDK home
     * @param sdkHome the SDK home, or null
     * @param requireExists if true, the location will only be returned if it also exists
     * @return the location of the this repository within a given SDK
     */
    @Nullable
    public File getRepositoryLocation(@Nullable File sdkHome, boolean requireExists,
            @NonNull FileOp fileOp) {
        if (sdkHome != null) {
            File dir = new File(sdkHome, FD_EXTRAS + separator + mDir
                    + separator + FD_M2_REPOSITORY);
            if (!requireExists || fileOp.isDirectory(dir)) {
                return dir;
            }
        }

        return null;
    }

    /**
     * Returns true if the given SDK repository is installed
     *
     * @param sdkHome the SDK installation location
     * @return true if the repository is installed
     */
    public boolean isInstalled(@Nullable File sdkHome, @NonNull FileOp fileOp) {
        return getRepositoryLocation(sdkHome, true, fileOp) != null;
    }

    /**
     * Returns true if the given SDK repository is installed
     *
     * @param sdkHandler the SDK to check
     * @return true if the repository is installed
     */
    public boolean isInstalled(@Nullable AndroidSdkHandler sdkHandler) {
        if (sdkHandler != null) {
            ProgressIndicator progress = new ConsoleProgressIndicator();
            RepoManager mgr = sdkHandler.getSdkManager(progress);
            return mgr.getPackages().getLocalPackages().containsKey(getPackageId());
        }

        return false;
    }

    public String getPackageId() {
        return String.format("extras;%s;%s", mDir, FD_M2_REPOSITORY);
    }

    /**
     * @deprecated For testability use
     * {@link #getHighestInstalledVersion(File, String, String, String, boolean, FileOp)}
     */
    @Deprecated
    public GradleCoordinate getHighestInstalledVersion(
            @Nullable File sdkHome,
            @NonNull String groupId,
            @NonNull String artifactId,
            @Nullable String filter,
            boolean allowPreview) {
        return getHighestInstalledVersion(sdkHome, groupId, artifactId, filter, allowPreview,
                FileOpUtils.create());
    }

    /**
     * Find the best matching {@link GradleCoordinate}
     *
     * @param sdkHome      the SDK installation
     * @param groupId      the artifact group id
     * @param artifactId   the artifact id
     * @param filter       an optional filter which the matched coordinate's version name must start
     *                     with
     * @param allowPreview whether preview versions are allowed to match
     * @param fileOp       To allow mocking of filesystem operations.
     * @return the best (highest version) matching coordinate, or null if none were found
     */
    @Nullable
    public GradleCoordinate getHighestInstalledVersion(
            @Nullable File sdkHome,
            @NonNull String groupId,
            @NonNull String artifactId,
            @Nullable String filter,
            boolean allowPreview,
            @NonNull FileOp fileOp) {
        File repository = getRepositoryLocation(sdkHome, true, fileOp);
        if (repository != null) {
            return MavenRepositories.getHighestInstalledVersion(
                    groupId, artifactId, repository, filter, allowPreview, fileOp);
        }

        return null;
    }

    /**
     * Returns the SDK repository which contains the given artifact, of null if a matching directory
     * cannot be found in any SDK directory.
     */
    @Nullable
    public static SdkMavenRepository find(
            @NonNull File sdkLocation,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull FileOp fileOp) {
        for (SdkMavenRepository repository : values()) {
            File repositoryLocation =
                    repository.getRepositoryLocation(sdkLocation, true, fileOp);

            if (repositoryLocation != null) {
                File artifactIdDirectory =
                        MavenRepositories.getArtifactIdDirectory(repositoryLocation, groupId, artifactId);

                if (fileOp.exists(artifactIdDirectory)) {
                    return repository;
                }
            }
        }

        return null;
    }

    /** The directory name of the repository inside the extras folder */
    @NonNull
    public String getDirName() {
        return mDir;
    }

    /**
     * Given {@link RepoPackage}-style {@link RepoPackage#getPath() path}, get the
     * {@link GradleCoordinate} for the package (assuming it is a maven-style package).

     * @return The {@link GradleCoordinate}, or null if the package is not a maven-style package.
     */
    @Nullable
    public static GradleCoordinate getCoordinateFromSdkPath(@NonNull String path) {
        String prefix = String
          .join(Character.toString(RepoPackage.PATH_SEPARATOR), FD_EXTRAS, FD_M2_REPOSITORY, "");
        if (!path.startsWith(prefix)) {
            return null;
        }
        List<String> components = Lists
          .newArrayList(path.split(Character.toString(RepoPackage.PATH_SEPARATOR)));
        String version = components.remove(components.size() - 1);
        String artifact = components.remove(components.size() - 1);
        String group = String.join(".", components.subList(2, components.size()));
        List<GradleCoordinate.RevisionComponent> revisionComponents = GradleCoordinate
          .parseRevisionNumber(version);
        return new GradleCoordinate(group, artifact, revisionComponents
          .toArray(new GradleCoordinate.RevisionComponent[revisionComponents.size()]));
    }

    /**
     * Given a collection of {@link RepoPackage}s, find the one that best matches the given
     * {@link GradleCoordinate} (that is, the one that corresponds to the maven artifact with the
     * same version, or the highest package matching a coordinate with +).
     *
     * @return The best package, or {@code null} if none was found.
     */
    @Nullable
    public static RepoPackage findBestPackageMatching(@NonNull GradleCoordinate coordinate,
      @NonNull Collection<? extends RepoPackage> packages) {
        RepoPackage result = null;
        GradleCoordinate resultCoordinate = null;
        for (RepoPackage p : packages) {
            GradleCoordinate test = getCoordinateFromSdkPath(p.getPath());
            if (test != null && test.matches(coordinate) && (resultCoordinate == null
              || GradleCoordinate.COMPARE_PLUS_LOWER.compare(test, resultCoordinate) > 0)) {
                result = p;
                resultCoordinate = test;
            }
        }
        return result;
    }
}
