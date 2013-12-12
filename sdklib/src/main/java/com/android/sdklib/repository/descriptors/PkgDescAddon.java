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

package com.android.sdklib.repository.descriptors;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;

/**
 * Implementation detail of {@link PkgDesc} for add-ons.
 * Do not use this class directly.
 * To create an instance use {@link PkgDesc#newAddon} instead.
 */
final class PkgDescAddon extends PkgDesc {

    public static final String ADDON_NAME         = "name";                 //$NON-NLS-1$
    public static final String ADDON_VENDOR       = "vendor";               //$NON-NLS-1$
    public static final String ADDON_API          = "api";                  //$NON-NLS-1$
    public static final String ADDON_DESCRIPTION  = "description";          //$NON-NLS-1$
    public static final String ADDON_LIBRARIES    = "libraries";            //$NON-NLS-1$
    public static final String ADDON_DEFAULT_SKIN = "skin";                 //$NON-NLS-1$
    public static final String ADDON_USB_VENDOR   = "usb-vendor";           //$NON-NLS-1$
    public static final String ADDON_REVISION     = "revision";             //$NON-NLS-1$
    public static final String ADDON_REVISION_OLD = "version";              //$NON-NLS-1$

    private @NonNull  final AndroidVersion mVersion;
    private @NonNull  final MajorRevision mRevision;
    private @Nullable final String mAddonPath;
    private @Nullable final String mAddonVendor;
    private @Nullable final IAddonDesc mTargetHashProvider;

    /**
     * Creates an add-on pkg description where the target hash isn't determined yet.
     */
    PkgDescAddon(@NonNull AndroidVersion version,
                 @NonNull MajorRevision revision,
                 @NonNull IAddonDesc targetHashProvider) {
        mVersion = version;
        mRevision = revision;
        mTargetHashProvider = targetHashProvider;
        mAddonPath = null;
        mAddonVendor = null;
    }

    PkgDescAddon(@NonNull AndroidVersion version,
                        @NonNull MajorRevision revision,
                        @NonNull String addonVendor,
                        @NonNull String addonName) {
        mVersion = version;
        mRevision = revision;
        mAddonVendor = addonVendor;
        mTargetHashProvider = null;
        mAddonPath = AndroidTargetHash.getAddonHashString(addonVendor, addonName, version);
    }

    @NonNull
    @Override
    public PkgType getType() {
        return PkgType.PKG_ADDONS;
    }

    @NonNull
    @Override
    public AndroidVersion getAndroidVersion() {
        return mVersion;
    }

    @Override
    public MajorRevision getMajorRevision() {
        return mRevision;
    }

    @Override
    public String getVendorId() {
        if (mTargetHashProvider != null) {
            return mTargetHashProvider.getVendorId();
        }
        return mAddonVendor;
    }

    /** The "path" of a Add-on is its Target Hash. */
    @NonNull
    @Override
    public String getPath() {
        if (mTargetHashProvider != null) {
            return mTargetHashProvider.getTargetHash();
        }
        return mAddonPath;
    }

    @Override
    public FullRevision getMinToolsRev() {
        return null;
    }

    @Override
    public boolean isUpdateFor(IPkgDesc existingDesc) {
        return isGenericUpdateFor(existingDesc);
    }
}
