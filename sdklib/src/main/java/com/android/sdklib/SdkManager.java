/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.repository.Revision;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.utils.ILogger;

import java.io.File;
import java.util.EnumSet;

/**
 * This class is obsolete. Do not use it in new code.
 */
public class SdkManager {

    @SuppressWarnings("unused")
    private static final boolean DEBUG = System.getenv("SDKMAN_DEBUG") != null;        //$NON-NLS-1$

    /**
     * Embedded reference to the new local SDK object.
     */
    private final LocalSdk mLocalSdk;

    /**
     * Create a new {@link SdkManager} instance. External users should use
     * {@link #createManager(String, ILogger)}.
     *
     * @param osSdkPath the location of the SDK.
     */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    protected SdkManager(@NonNull String osSdkPath) {
        mLocalSdk = new LocalSdk(new File(osSdkPath));
    }

    /**
     * Creates an @{linkplain SdkManager} for an existing @{link LocalSdk}.
     *
     * @param localSdk the SDK to use with the SDK manager
     */
    private SdkManager(@NonNull LocalSdk localSdk) {
        mLocalSdk = localSdk;
    }

    /**
     * Creates an {@link SdkManager} for a given sdk location.
     *
     * @param osSdkPath the location of the SDK.
     * @param log       the ILogger object receiving warning/error from the parsing.
     * @return the created {@link SdkManager} or null if the location is not valid.
     */
    @Nullable
    public static SdkManager createManager(
            @NonNull String osSdkPath,
            @NonNull ILogger log) {
        try {
            SdkManager manager = new SdkManager(osSdkPath);
            manager.reloadSdk();

            return manager;
        } catch (Throwable throwable) {
            log.error(throwable, "Error parsing the sdk.");
        }

        return null;
    }

    /**
     * Creates an @{linkplain SdkManager} for an existing @{link LocalSdk}.
     *
     * @param localSdk the SDK to use with the SDK manager
     */
    @NonNull
    @SuppressWarnings("unused")
    public static SdkManager createManager(@NonNull LocalSdk localSdk) {
        return new SdkManager(localSdk);
    }

    @NonNull
    public LocalSdk getLocalSdk() {
        return mLocalSdk;
    }

    public void reloadSdk() {
        mLocalSdk.clearLocalPkg(PkgType.PKG_ALL);
    }

    /**
     * @deprecated Use {@link #reloadSdk()} instead
     */
    @Deprecated
    public void reloadSdk(@NonNull @SuppressWarnings("UnusedParameters") ILogger log) {
        reloadSdk();
    }

    public boolean hasChanged() {
        return mLocalSdk.hasChanged(EnumSet.of(
                PkgType.PKG_BUILD_TOOLS,
                PkgType.PKG_PLATFORM,
                PkgType.PKG_ADDON));
    }

    /**
     * @deprecated Use {@link #hasChanged()} instead
     */
    @Deprecated
    @SuppressWarnings("unused")
    public boolean hasChanged(@Nullable ILogger log) {
        return hasChanged();
    }

    /**
     * @deprecated Use {@link #getLocalSdk()} and {@link LocalSdk#getLocation()} instead
     */
    @Deprecated
    @NonNull
    public String getLocation() {
        File f = mLocalSdk.getLocation();
        // Our LocalSdk is created with a file path, so we know the location won't be null.
        assert f != null;
        return f.getPath();
    }

    /**
     * <p>Returns the targets (platforms & addons) that are available in the SDK. The target list is
     * created on demand the first time then cached. It will not refreshed unless
     * {@link #reloadSdk(ILogger)} is called.
     *
     * <p>The array can be empty but not null.
     */
    @NonNull
    public IAndroidTarget[] getTargets() {
        return mLocalSdk.getTargets();
    }

    /**
     * Returns the highest build-tool revision known. Can be null.
     *
     * @return The highest build-tool revision known, or null.
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool() {
        return mLocalSdk.getLatestBuildTool();
    }

    /**
     * Returns the {@link BuildToolInfo} for the given revision.
     *
     * @param revision The requested revision.
     * @return A {@link BuildToolInfo}. Can be null if {@code revision} is null or is not part of
     * the known set returned by getBuildTools().
     */
    @Nullable
    public BuildToolInfo getBuildTool(@Nullable Revision revision) {
        return mLocalSdk.getBuildTool(revision);
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    @Nullable
    public IAndroidTarget getTargetFromHashString(@Nullable String hash) {
        return mLocalSdk.getTargetFromHashString(hash);
    }

    /**
     * <p>Returns the greatest {@link LayoutlibVersion} found amongst all platform targets currently
     * loaded in the SDK.
     *
     * <p>We only started recording Layoutlib Versions recently in the platform meta data so it's
     * possible to have an SDK with many platforms loaded but no layoutlib version defined.
     *
     * @return The greatest {@link LayoutlibVersion} or null if none is found.
     * @deprecated This does NOT solve the right problem and will be changed later.
     */
    @Deprecated
    @Nullable
    public LayoutlibVersion getMaxLayoutlibVersion() {
        LayoutlibVersion maxVersion = null;

        for (IAndroidTarget target : getTargets()) {
            if (target instanceof PlatformTarget) {
                LayoutlibVersion lv = ((PlatformTarget) target).getLayoutlibVersion();
                if (lv != null) {
                    if (maxVersion == null || lv.compareTo(maxVersion) > 0) {
                        maxVersion = lv;
                    }
                }
            }
        }

        return maxVersion;
    }

    // -------------

    public static class LayoutlibVersion implements Comparable<LayoutlibVersion> {

        private final int mApi;

        private final int mRevision;

        public static final int NOT_SPECIFIED = 0;

        public LayoutlibVersion(int api, int revision) {
            mApi = api;
            mRevision = revision;
        }

        public int getApi() {
            return mApi;
        }

        public int getRevision() {
            return mRevision;
        }

        @Override
        public int compareTo(@NonNull LayoutlibVersion rhs) {
            boolean useRev = this.mRevision > NOT_SPECIFIED && rhs.mRevision > NOT_SPECIFIED;
            int lhsValue = (this.mApi << 16) + (useRev ? this.mRevision : 0);
            int rhsValue = (rhs.mApi << 16) + (useRev ? rhs.mRevision : 0);
            return lhsValue - rhsValue;
        }
    }
}
