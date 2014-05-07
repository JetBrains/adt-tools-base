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
import com.android.sdklib.internal.repository.packages.License;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;

/**
 * Implementation detail of {@link PkgDesc} for add-ons.
 * Do not use this class directly.
 * To create an instance use {@link PkgDesc.Builder#newAddon} instead.
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

    private @Nullable final String mAddonPath;
    private @Nullable final IAddonDesc mTargetHashProvider;

    /**
     * Add-on specific attributes should be either:
     * - targetHashProvider is used if not null, and addonVendor/addonName are ignored.
     * - otherwise addonVendor/addonName are used and should not be null.
     *
     * targetHashProvider is used to create an add-on pkg description
     * where the target hash isn't determined yet.
     */
    PkgDescAddon(@NonNull PkgType type,
                 @Nullable License license,
                 @Nullable String listDisplay,
                 @Nullable String descriptionShort,
                 @Nullable String descriptionUrl,
                 boolean isObsolete,
                 @Nullable FullRevision fullRevision,
                 @Nullable MajorRevision majorRevision,
                 @Nullable AndroidVersion androidVersion,
                 @Nullable IdDisplay tag,
                 @Nullable IdDisplay vendor,
                 @Nullable FullRevision minToolsRev,
                 @Nullable FullRevision minPlatformToolsRev,
                 @Nullable String addonNameId,
                 @Nullable IAddonDesc targetHashProvider) {
        super(type,
              license,
              listDisplay,
              descriptionShort,
              descriptionUrl,
              isObsolete,
              fullRevision,
              majorRevision,
              androidVersion,
              null,     //path
              tag,
              vendor,
              minToolsRev,
              minPlatformToolsRev,
              null,     //customIsUpdateFor
              null);    //customPath

        String addonVendor = vendor == null ? null : vendor.getId();
        assert targetHashProvider != null || (addonVendor != null && addonNameId != null);
        mTargetHashProvider = targetHashProvider;
        mAddonPath = targetHashProvider != null ? null :
                     AndroidTargetHash.getAddonHashString(addonVendor, addonNameId, androidVersion);
    }

    @Override
    public IdDisplay getVendor() {
        if (mTargetHashProvider != null) {
            String vid = mTargetHashProvider.getVendorId();
            return new IdDisplay(vid, vid);     // TODO inject vendor-display
        }
        return super.getVendor();
    }

    /** The "path" of an add-on is its target hash. */
    @NonNull
    @Override
    public String getPath() {
        if (mTargetHashProvider != null) {
            return mTargetHashProvider.getTargetHash();
        }
        return mAddonPath;
    }
}
