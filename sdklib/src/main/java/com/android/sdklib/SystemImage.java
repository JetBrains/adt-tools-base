/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.repository.descriptors.IdDisplay;

import java.io.File;
import java.util.Locale;


/**
 * Describes a system image as used by an {@link IAndroidTarget}.
 * A system image has an installation path, a location type, a tag and an ABI type.
 */
public class SystemImage implements ISystemImage {

    public static final String ANDROID_PREFIX = "android-";                 //$NON-NLS-1$

    public static final IdDisplay DEFAULT_TAG = new IdDisplay("default",    //$NON-NLS-1$
                                                              "Default");   //$NON-NLS-1$

    private final LocationType mLocationtype;
    private final IdDisplay mTag;
    private final String mAbiType;
    private final File mLocation;
    private final File[] mSkins;

    /**
     * Creates a {@link SystemImage} description for an existing system image folder.
     *
     * @param location The location of an installed system image.
     * @param locationType Where the system image folder is located for this ABI.
     * @param tag The tag of the system-image. Use {@link #DEFAULT_TAG} for backward compatibility.
     * @param abiType The ABI type. For example, one of {@link SdkConstants#ABI_ARMEABI},
     *          {@link SdkConstants#ABI_ARMEABI_V7A}, {@link SdkConstants#ABI_INTEL_ATOM} or
     *          {@link SdkConstants#ABI_MIPS}.
     * @param skins A non-null, possibly empty list of skins specific to this system image.
     */
    public SystemImage(
            @NonNull File location,
            @NonNull LocationType locationType,
            @NonNull IdDisplay tag,
            @NonNull String abiType,
            @NonNull File[] skins) {
        mLocation = location;
        mLocationtype = locationType;
        mTag = tag;
        mAbiType = abiType;
        mSkins = skins;
    }

    /**
     * Creates a {@link SystemImage} description for a non-existing system image folder.
     * The actual location is computed based on the {@code locationtype}.
     *
     * @param sdkManager The current SDK manager.
     * @param locationType Where the system image folder is located for this ABI.
     * @param tag The tag of the system-image. Use {@link #DEFAULT_TAG} for backward compatibility.
     * @param abiType The ABI type. For example, one of {@link SdkConstants#ABI_ARMEABI},
     *          {@link SdkConstants#ABI_ARMEABI_V7A}, {@link SdkConstants#ABI_INTEL_ATOM} or
     *          {@link SdkConstants#ABI_MIPS}.
     * @param skins A non-null, possibly empty list of skins specific to this system image.
     * @throws IllegalArgumentException if the {@code target} used for
     *         {@link ISystemImage.LocationType#IN_SYSTEM_IMAGE} is not a {@link PlatformTarget}.
     */
    public SystemImage(
            @NonNull SdkManager sdkManager,
            @NonNull IAndroidTarget target,
            @NonNull LocationType locationType,
            @NonNull IdDisplay tag,
            @NonNull String abiType,
            @NonNull File[] skins) {
        mLocationtype = locationType;
        mTag = tag;
        mAbiType = abiType;
        mSkins = skins;

        File location = null;
        switch(locationType) {
        case IN_PLATFORM_LEGACY:
            location = new File(target.getLocation(), SdkConstants.OS_IMAGES_FOLDER);
            break;

        case IN_PLATFORM_SUBFOLDER:
            location = FileOp.append(target.getLocation(), SdkConstants.OS_IMAGES_FOLDER, abiType);
            break;

        case IN_SYSTEM_IMAGE:
            if (!target.isPlatform()) {
                throw new IllegalArgumentException(
                        "Add-ons do not support the system-image location type"); //$NON-NLS-1$
            }

            location = getCanonicalFolder(sdkManager.getLocation(),
                                          target.getVersion(),
                                          tag.getId(),
                                          abiType);
            break;
        default:
            // This is not supposed to happen unless LocationType is
            // extended without adjusting this code.
            assert false : "SystemImage used with an incorrect locationType";       //$NON-NLS-1$
        }
        mLocation = location;
    }

    /**
     * Static helper method that returns the canonical path for a system-image that uses
     * the {@link ISystemImage.LocationType#IN_SYSTEM_IMAGE} location type.
     * <p/>
     * Such an image is located in {@code SDK/system-images/android-N/tag/abiType}.
     * For this reason this method requires the root SDK as well as the platform, tag abd ABI type.
     *
     * @param sdkOsPath The OS path to the SDK.
     * @param platformVersion The platform version.
     * @param tagId An optional tag. If null, not tag folder is used.
     *          For legacy, use {@code SystemImage.DEFAULT_TAG.getId()}.
     * @param abiType An optional ABI type. If null, the parent directory is returned.
     * @return A file that represents the location of the canonical system-image folder
     *         for this configuration.
     */
    @NonNull
    public static File getCanonicalFolder(
            String sdkOsPath,
            AndroidVersion platformVersion,
            String tagId,
            String abiType) {
        File root = FileOp.append(
                sdkOsPath,
                SdkConstants.FD_SYSTEM_IMAGES,
                ANDROID_PREFIX + platformVersion.getApiString());
        if (tagId != null) {
            root = FileOp.append(root, tagId);
        }
        if (abiType == null) {
            return root;
        } else {
            return FileOp.append(root, abiType);
        }
    }

    /** Returns the actual location of an installed system image. */
    @Override
    @NonNull
    public File getLocation() {
        return mLocation;
    }

    /** Indicates the location strategy for this system image in the SDK. */
    @Override
    @NonNull
    public LocationType getLocationType() {
        return mLocationtype;
    }

    /** Returns the tag of the system image. */
    @Override
    @NonNull
    public IdDisplay getTag() {
        return mTag;
    }

    /**
     * Returns the ABI type. For example, one of {@link SdkConstants#ABI_ARMEABI},
     * {@link SdkConstants#ABI_ARMEABI_V7A}, {@link SdkConstants#ABI_INTEL_ATOM} or
     * {@link SdkConstants#ABI_MIPS}.
     * Cannot be null nor empty.
     */
    @Override
    @NonNull
    public String getAbiType() {
        return mAbiType;
    }

    @Override
    public File[] getSkins() {
        return mSkins;
    }

    /**
     * Sort by tag & ABI name only. This is what matters from a user point of view.
     */
    @Override
    public int compareTo(ISystemImage other) {
        int t = this.getTag().compareTo(other.getTag());
        if (t != 0) {
            return t;
        }
        return this.getAbiType().compareToIgnoreCase(other.getAbiType());
    }

    /**
     * Generates a string representation suitable for debug purposes.
     * The string is not intended to be displayed to the user.
     *
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String toString() {
        return String.format("SystemImage tag=%s, ABI=%s, location %s='%s'",           //$NON-NLS-1$
                mTag.getId(),
                mAbiType,
                mLocationtype.toString().replace('_', ' ').toLowerCase(Locale.US),
                mLocation
                );
    }


}
