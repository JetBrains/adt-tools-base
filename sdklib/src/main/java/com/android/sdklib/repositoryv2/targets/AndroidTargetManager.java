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
package com.android.sdklib.repositoryv2.targets;

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
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.androidTarget.MissingTarget;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds and allows access to all {@link IAndroidTarget}s in a given SDK, either using the old
 * {@link LocalSdk} mechanism or the new {@link AndroidSdkHandler}.
 */
public class AndroidTargetManager {

    /**
     * Cache of the {@link IAndroidTarget}s we created from platform and addon packages.
     */
    private Collection<IAndroidTarget> mTargets;

    /**
     * Pseudo-targets created when we have a system image with no corresponding platform or addon.
     * TODO: remove this concept if possible by looking up system images using {@link
     * SystemImageManager}.
     */
    private Collection<IAndroidTarget> mMissingTargets;

    private final FileOp mFop;

    private final AndroidSdkHandler mSdkHandler;

    /**
     * Map of package paths to errors encountered while loading creating the target.
     */
    private Map<String, String> mLoadErrors;

    /**
     * Create a manager using the new {@link AndroidSdkHandler}/{@link RepoManager} mechanism for
     * finding packages.
     */
    public AndroidTargetManager(@NonNull AndroidSdkHandler handler, @NonNull FileOp fop) {
        mSdkHandler = handler;
        mFop = fop;
    }

    /**
     * Returns the targets (platforms & addons) that are available in the SDK.
     */
    @NonNull
    public Collection<IAndroidTarget> getTargets(@NonNull ProgressIndicator progress) {
        if (mTargets == null) {
            Map<String, String> newErrors = Maps.newHashMap();
            List<IAndroidTarget> result = Lists.newArrayList();
            RepoManager manager = mSdkHandler.getSdkManager(progress);
            Map<AndroidVersion, PlatformTarget> platformTargets = Maps.newHashMap();
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.PlatformDetailsType) {
                    try {
                        PlatformTarget target = new PlatformTarget(p, mSdkHandler, mFop, progress);
                        result.add(target);
                        platformTargets.put(target.getVersion(), target);
                    } catch (IllegalArgumentException e) {
                        newErrors.put(p.getPath(), e.getMessage());
                    }
                }
            }
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.AddonDetailsType) {
                    AndroidVersion addonVersion = DetailsTypes
                      .getAndroidVersion((DetailsTypes.AddonDetailsType) details);
                    PlatformTarget baseTarget = platformTargets.get(addonVersion);
                    if (baseTarget != null) {
                        result.add(new AddonTarget(p, baseTarget,
                          mSdkHandler.getSystemImageManager(progress), mFop));
                    }
                }
            }
            mTargets = result;
            mLoadErrors = newErrors;
        }
        return mTargets;
    }

    @NonNull
    public Collection<MissingTarget> getMissingTargets(@NonNull ProgressIndicator progress) {
        // The need for this should go away with adoption of SystemImageManager, since the
        // point is to allow access to system images without a corresponding target. If we
        // access the images directly, there's no need for missing targets.
        Set<ISystemImage> foundImages = Sets.newHashSet();
        for (IAndroidTarget target : getTargets(progress)) {
            foundImages.addAll(Arrays.asList(target.getSystemImages()));
        }
        Map<MissingTarget, MissingTarget> missingTargets = Maps.newHashMap();
        for (ISystemImage image : mSdkHandler.getSystemImageManager(progress).getImages()) {
            if (!foundImages.contains(image)) {
                IdDisplay vendor = image.getAddonVendor();
                MissingTarget target = new MissingTarget(
                  vendor == null ? null : vendor.getDisplay(),
                  image.getTag().getDisplay(),
                  ((SystemImage) image).getAndroidVersion());
                if (missingTargets.containsKey(target)) {
                    target = missingTargets.get(target);
                } else {
                    missingTargets.put(target, target);
                }
                target.addSystemImage(image);
            }
        }
        return missingTargets.keySet();
    }

    /**
     * Returns the targets (possibly including pseudo-targets containing system images with no
     * associated target) that are available in the SDK.
     */
    @NonNull
    public Collection<IAndroidTarget> getTargets(boolean includeMissing,
            @NonNull ProgressIndicator progress) {
        if (includeMissing) {
            if (mMissingTargets == null) {
                List<IAndroidTarget> result = Lists.newArrayList();
                result.addAll(getMissingTargets(progress));
                result.addAll(getTargets(progress));
                mMissingTargets = result;
            }
            return mMissingTargets;
        }
        else {
            return getTargets(progress);
        }
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
            for (IAndroidTarget target : getTargets(true, progress)) {
                if (target != null && hash.equals(AndroidTargetHash.getTargetHashString(target))) {
                    return target;
                }
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
}
