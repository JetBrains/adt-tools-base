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
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.google.common.base.Objects;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;


/**
 * Describes a system image as used by an {@link IAndroidTarget}.
 * A system image has an installation path, a location type, a tag and an ABI type.
 *
 * @deprecated in favor of {@link com.android.sdklib.repositoryv2.targets.SystemImage}
 */
@Deprecated
public class SystemImage implements ISystemImage {

    public static final IdDisplay DEFAULT_TAG = new IdDisplay("default",    //$NON-NLS-1$
                                                              "Default");   //$NON-NLS-1$

    @Deprecated
    private final IdDisplay mTag;
    private final IdDisplay mAddonVendor;
    private final String mAbiType;
    private final File mLocation;
    private final File[] mSkins;
    private final Revision mRevision;

    /**
     * Creates a {@link SystemImage} description for an existing platform system image folder.
     *
     * @param location The location of an installed system image.
     * @param locationType Where the system image folder is located for this ABI.
     * @param tag The tag of the system-image. Use {@link #DEFAULT_TAG} for backward compatibility.
     * @param abiType The ABI type. For example, one of {@link SdkConstants#ABI_ARMEABI},
     *          {@link SdkConstants#ABI_ARMEABI_V7A}, {@link SdkConstants#ABI_INTEL_ATOM} or
     *          {@link SdkConstants#ABI_MIPS}.
     * @param skins A non-null, possibly empty list of skins specific to this system image.
     * @param revision The revision of this image.
     */
    public SystemImage(
            @NonNull  File location,
            @NonNull  LocationType locationType,
            @NonNull  IdDisplay tag,
            @NonNull  String abiType,
            @NonNull  File[] skins,
            @NonNull  Revision revision) {
        this(location, locationType, tag, null /*addonVendor*/, abiType, skins, revision);
    }

    /**
     * Creates a {@link SystemImage} description for an existing system image folder,
     * for either platform or add-on.
     *
     * @param location The location of an installed system image.
     * @param locationType Where the system image folder is located for this ABI.
     * @param tagName The tag of the system-image.
     *                For an add-on, the tag-id must match the add-on's name-id.
     * @param addonVendor Non-null add-on vendor name. Null for platforms.
     * @param abiType The ABI type. For example, one of {@link SdkConstants#ABI_ARMEABI},
     *          {@link SdkConstants#ABI_ARMEABI_V7A}, {@link SdkConstants#ABI_INTEL_ATOM} or
     *          {@link SdkConstants#ABI_MIPS}.
     * @param skins A non-null, possibly empty list of skins specific to this system image.
     * @param revision The revision of this image.
     */
    public SystemImage(
            @NonNull  File location,
            @NonNull  LocationType locationType,
            @NonNull  IdDisplay tagName,
            @Nullable IdDisplay addonVendor,
            @NonNull  String abiType,
            @NonNull  File[] skins,
            @NonNull  Revision revision) {
        mLocation = location;
        mTag = tagName;
        mAddonVendor = addonVendor;
        mAbiType = abiType;
        mSkins = skins;
        mRevision = revision;
    }

    /** Returns the actual location of an installed system image. */
    @NonNull
    @Override
    public File getLocation() {
        return mLocation;
    }

    /** Returns the tag of the system image. */
    @NonNull
    @Override
    public IdDisplay getTag() {
        return mTag;
    }

    /** Returns the vendor for an add-on's system image, or null for a platform system-image. */
    @Nullable
    @Override
    public IdDisplay getAddonVendor() {
        return mAddonVendor;
    }

    /**
     * Returns the ABI type.
     * See {@link Abi} for a full list.
     * Cannot be null nor empty.
     */
    @NonNull
    @Override
    public String getAbiType() {
        return mAbiType;
    }

    @NonNull
    @Override
    public File[] getSkins() {
        return mSkins;
    }

    @NonNull
    @Override
    public Revision getRevision() {
        return mRevision;
    }

    /**
     * Sort by tag & ABI name, then arbitrarily (but consistently).
     */
    @Override
    public int compareTo(ISystemImage other) {
        int t = this.getTag().compareTo(other.getTag());
        if (t != 0) {
            return t;
        }
        t = this.getAbiType().compareToIgnoreCase(other.getAbiType());
        if (t != 0) {
            return t;
        }
        return hashCode() - other.hashCode();
    }

    /**
     * Generates a string representation suitable for debug purposes.
     * The string is not intended to be displayed to the user.
     *
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SystemImage");
        if (mAddonVendor != null) {
            sb.append(" addon-vendor=").append(mAddonVendor.getId()).append(',');
        }
        sb.append(" tag=").append(mTag.getId());
        sb.append(", ABI=").append(mAbiType);
        sb.append(", location")
          .append("='")
          .append(mLocation)
          .append("'");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SystemImage)) {
          return false;
        }
        SystemImage other = (SystemImage)o;
        return mTag.equals(other.mTag) && mAbiType.equals(other.getAbiType()) && Objects.equal(mAddonVendor, other.mAddonVendor)
               && mLocation.equals(other.mLocation) && Arrays.equals(mSkins, other.mSkins);
    }

    @Override
    public int hashCode() {
        int hashCode = Objects.hashCode(mTag, mAbiType, mAddonVendor, mLocation);
        hashCode *= 37;
        hashCode += Arrays.hashCode(mSkins);
        return hashCode;
    }
}
