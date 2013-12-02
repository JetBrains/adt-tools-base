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

package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;



/**
 * Helper methods to manipulate hash strings used by {@link IAndroidTarget#hashString()}.
 */
public abstract class AndroidTargetHash {

    /**
     * Prefix used to build hash strings for platform targets
     * @see SdkManager#getTargetFromHashString(String)
     */
    private static final String PLATFORM_HASH_PREFIX = "android-";

    /**
     * String to compute hash for add-on targets.
     * Format is vendor:name:apiVersion
     * */
    public static final String ADD_ON_FORMAT = "%s:%s:%s"; //$NON-NLS-1$

    /**
     * String used to get a hash to the platform target.
     * This format is compatible with the PlatformPackage.installId().
     */
    static final String PLATFORM_HASH = PLATFORM_HASH_PREFIX + "%s";

    /**
     * Returns the hash string for a given platform version.
     *
     * @param version A non-null platform version.
     * @return A non-null hash string uniquely representing this platform target.
     */
    @NonNull
    public static String getPlatformHashString(@NonNull AndroidVersion version) {
        return String.format(AndroidTargetHash.PLATFORM_HASH, version.getApiString());
    }

    /**
     * Returns the {@link com.android.sdklib.AndroidVersion} for the given hash string,
     * if it represents a platform. If the hash string represents a preview platform,
     * the returned {@link AndroidVersion} will have an unknown API level (set to 1).
     *
     * @param hashString the hash string
     * @return a platform, or null
     */
    @Nullable
    public static AndroidVersion getPlatformVersion(@NonNull String hashString) {
        if (hashString.startsWith(PLATFORM_HASH_PREFIX)) {
            String suffix = hashString.substring(PLATFORM_HASH_PREFIX.length());
            if (!suffix.isEmpty()) {
                if (Character.isDigit(suffix.charAt(0))) {
                    int api = Integer.parseInt(suffix);
                    return new AndroidVersion(api, null);
                } else {
                    return new AndroidVersion(1, suffix);
                }
            }
        }

        return null;
    }

    /**
     * Returns the hash string for a given add-on.
     *
     * @param addonVendor A non-null
     * @param addonName
     * @param version A non-null platform version (the addon's base platform version)
     * @return A non-null hash string uniquely representing this add-on target.
     */
    public static String getAddonHashString(
            @NonNull String addonVendor,
            @NonNull String addonName,
            @NonNull AndroidVersion version) {
        return String.format(ADD_ON_FORMAT,
                addonVendor,
                addonName,
                version.getApiString());
    }

    /**
     * Returns the hash string for a given target (add-on or platform.)
     *
     * @param target A non-null target.
     * @return A non-null hash string uniquely representing this target.
     */
    public static String getTargetHashString(@NonNull IAndroidTarget target) {
        if (target.isPlatform()) {
            return getPlatformHashString(target.getVersion());
        } else {
            return getAddonHashString(
                    target.getVendor(),
                    target.getName(),
                    target.getVersion());
        }
    }

    /**
     * Given a hash string, indicates whether this is a platform hash string.
     * If not, it's an addon hash string.
     *
     * @param hashString The hash string to test.
     * @return True if this hash string starts by the platform prefix.
     */
    public static boolean isPlatform(@NonNull String hashString) {
        return hashString.startsWith(PLATFORM_HASH_PREFIX);
    }

}
