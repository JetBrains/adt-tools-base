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
package com.android.sdklib.repository.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * Finds and allows access to all {@link IAndroidTarget}s in a given SDK.
 */
public class AndroidTargetManager {

    /**
     * Cache of the {@link IAndroidTarget}s we created from platform and addon packages.
     */
    private Map<LocalPackage, IAndroidTarget> mTargets;

    private final FileOp mFop;

    private final AndroidSdkHandler mSdkHandler;

    /**
     * Map of package paths to errors encountered while loading creating the target.
     */
    private Map<String, String> mLoadErrors;

    private Comparator<LocalPackage> TARGET_COMPARATOR;

    /**
     * Create a manager using the new {@link AndroidSdkHandler}/{@link RepoManager} mechanism for
     * finding packages.
     */
    public AndroidTargetManager(@NonNull AndroidSdkHandler handler, @NonNull FileOp fop) {
        mSdkHandler = handler;
        mFop = fop;
    }

    /**
     * Returns the targets (platforms and addons) that are available in the SDK, sorted in
     * ascending order by API level.
     */
    @NonNull
    public Collection<IAndroidTarget> getTargets(@NonNull ProgressIndicator progress) {
        return getTargetMap(progress).values();
    }

    @NonNull
    private Map<LocalPackage, IAndroidTarget> getTargetMap(@NonNull ProgressIndicator progress) {
        if (mTargets == null) {
            Map<String, String> newErrors = Maps.newHashMap();
            TARGET_COMPARATOR = new Comparator<LocalPackage>() {
                @Override
                public int compare(LocalPackage o1, LocalPackage o2) {
                    DetailsTypes.ApiDetailsType details1 = (DetailsTypes.ApiDetailsType) o1
                            .getTypeDetails();
                    DetailsTypes.ApiDetailsType details2 = (DetailsTypes.ApiDetailsType) o2
                            .getTypeDetails();
                    AndroidVersion version1 = details1.getAndroidVersion();
                    AndroidVersion version2 = details2.getAndroidVersion();
                    return ComparisonChain.start()
                            .compare(version1, version2)
                            .compare(o1.getPath(), o2.getPath())
                            .compare(details1.getClass().getName(), details2.getClass().getName())
                            .result();
                }
            };
            Map<LocalPackage, IAndroidTarget> result = Maps.newTreeMap(TARGET_COMPARATOR);
            RepoManager manager = mSdkHandler.getSdkManager(progress);
            Map<AndroidVersion, PlatformTarget> platformTargets = Maps.newHashMap();
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.PlatformDetailsType) {
                    try {
                        PlatformTarget target = new PlatformTarget(p, mSdkHandler, mFop, progress);
                        result.put(p, target);
                        platformTargets.put(target.getVersion(), target);
                    } catch (IllegalArgumentException e) {
                        newErrors.put(p.getPath(), e.getMessage());
                    }
                }
            }
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.AddonDetailsType) {
                    AndroidVersion addonVersion =
                            ((DetailsTypes.AddonDetailsType)details).getAndroidVersion();
                    PlatformTarget baseTarget = platformTargets.get(addonVersion);
                    if (baseTarget != null) {
                        result.put(p, new AddonTarget(p, baseTarget,
                                                      mSdkHandler.getSystemImageManager(progress), progress, mFop));
                    }
                }
            }
            for (LocalPackage p : manager.getPackages().getLocalPackagesForPrefix(SdkConstants.FD_ANDROID_SOURCES)) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.ApiDetailsType) {
                    PlatformTarget target = platformTargets.get(
                            ((DetailsTypes.ApiDetailsType)details).getAndroidVersion());
                    if (target != null) {
                        target.setSources(p.getLocation());
                    }
                }
            }
            mTargets = result;
            mLoadErrors = newErrors;
        }
        return mTargets;
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    @Nullable
    public IAndroidTarget getTargetFromHashString(@Nullable String hash,
            @NonNull ProgressIndicator progress) {
        if (hash != null) {
            for (IAndroidTarget target : getTargets(progress)) {
                if (target != null && hash.equals(AndroidTargetHash.getTargetHashString(target))) {
                    return target;
                }
            }
        }
        return null;
    }

    /**
     * Returns first target found with API level no lower than the minimum provided.
     * @param minimumApiLevel minimum api level desired for target.
     * @param progress progress indicator.
     * @return a matching {@link IAndroidTarget} or null
     */
    @Nullable
    public IAndroidTarget getTargetOfAtLeastApiLevel(
            int minimumApiLevel, @NonNull ProgressIndicator progress) {
        for (IAndroidTarget target : getTargets(progress)) {
            if (target.getVersion().getApiLevel() >= minimumApiLevel) {
                return target;
            }
        }
        return null;
    }

    /**
     * Returns the error, if any, encountered when error creating a target for a package.
     */
    @Nullable
    public String getErrorForPackage(@NonNull String path) {
        return mLoadErrors.get(path);
    }

    @Nullable
    public IAndroidTarget getTargetFromPackage(@NonNull LocalPackage p, @NonNull ProgressIndicator progress) {
        return getTargetMap(progress).get(p);
    }
}
