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
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;

import java.util.Locale;

/**
 * {@link PkgDesc} keeps information on individual SDK packages
 * (both local or remote packages definitions.)
 * <br/>
 * Packages have different attributes depending on their type.
 * <p/>
 * To create a new {@link PkgDesc}, use one of the package-specific constructors
 * provided here.
 * <p/>
 * To query packages capabilities, rely on {@link #getType()} and the {@code PkgDesc.hasXxx()}
 * methods provided in the base {@link PkgDesc}.
 */
public class PkgDesc implements IPkgDesc {
    private final PkgType mType;
    private final FullRevision mFullRevision;
    private final MajorRevision mMajorRevision;
    private final AndroidVersion mAndroidVersion;
    private final String mPath;
    private final IdDisplay mTag;
    private final String mVendorId;
    private final FullRevision mMinToolsRev;
    private final FullRevision mMinPlatformToolsRev;
    private final IIsUpdateFor mCustomIsUpdateFor;
    private final IGetPath mCustomPath;

    protected PkgDesc(@NonNull PkgType type,
                      @Nullable FullRevision fullRevision,
                      @Nullable MajorRevision majorRevision,
                      @Nullable AndroidVersion androidVersion,
                      @Nullable String path,
                      @Nullable IdDisplay tag,
                      @Nullable String vendorId,
                      @Nullable FullRevision minToolsRev,
                      @Nullable FullRevision minPlatformToolsRev,
                      @Nullable IIsUpdateFor customIsUpdateFor,
                      @Nullable IGetPath customPath) {
        mType = type;
        mFullRevision = fullRevision;
        mMajorRevision = majorRevision;
        mAndroidVersion = androidVersion;
        mPath = path;
        mTag = tag;
        mVendorId = vendorId;
        mMinToolsRev = minToolsRev;
        mMinPlatformToolsRev = minPlatformToolsRev;
        mCustomIsUpdateFor = customIsUpdateFor;
        mCustomPath = customPath;
    }

    @NonNull
    @Override
    public PkgType getType() {
        return mType;
    }

    @Override
    public final boolean hasFullRevision() {
        return getType().hasFullRevision();
    }

    @Override
    public final boolean hasMajorRevision() {
        return getType().hasMajorRevision();
    }

    @Override
    public final boolean hasAndroidVersion() {
        return getType().hasAndroidVersion();
    }

    @Override
    public final boolean hasPath() {
        return getType().hasPath();
    }

    @Override
    public final boolean hasTag() {
        return getType().hasTag();
    }

    @Override
    public boolean hasVendorId() {
        return getType().hasVendorId();
    }

    @Override
    public final boolean hasMinToolsRev() {
        return getType().hasMinToolsRev();
    }

    @Override
    public final boolean hasMinPlatformToolsRev() {
        return getType().hasMinPlatformToolsRev();
    }

    @Nullable
    @Override
    public FullRevision getFullRevision() {
        return mFullRevision;
    }

    @Nullable
    @Override
    public MajorRevision getMajorRevision() {
        return mMajorRevision;
    }

    @Nullable
    @Override
    public AndroidVersion getAndroidVersion() {
        return mAndroidVersion;
    }

    @Nullable
    @Override
    public String getPath() {
        if (mCustomPath != null) {
            return mCustomPath.getPath(this);
        } else {
            return mPath;
        }
    }

    @Nullable
    @Override
    public IdDisplay getTag() {
        return mTag;
    }

    @Nullable
    @Override
    public String getVendorId() {
        return mVendorId;
    }

    @Nullable
    @Override
    public FullRevision getMinToolsRev() {
        return mMinToolsRev;
    }

    @Nullable
    @Override
    public FullRevision getMinPlatformToolsRev() {
        return mMinPlatformToolsRev;
    }

    //---- Updating ----

    /**
     * Computes the most general case of {@link #isUpdateFor(IPkgDesc)}.
     * Individual package types use this and complement with their own specific cases
     * as needed.
     *
     * @param existingDesc A non-null package descriptor to compare with.
     * @return True if this package descriptor would generally update the given one.
     */
    @Override
    public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
        if (mCustomIsUpdateFor != null) {
            return mCustomIsUpdateFor.isUpdateFor(this, existingDesc);
        } else {
            return isGenericUpdateFor(existingDesc);
        }
    }

    /**
     * Computes the most general case of {@link #isUpdateFor(IPkgDesc)}.
     * Individual package types use this and complement with their own specific cases
     * as needed.
     *
     * @param existingDesc A non-null package descriptor to compare with.
     * @return True if this package descriptor would generally update the given one.
     */
    private boolean isGenericUpdateFor(@NonNull IPkgDesc existingDesc) {

        if (existingDesc == null || !getType().equals(existingDesc.getType())) {
            return false;
        }

        // Packages that have an Android version can generally only be updated
        // for the same Android version (otherwise it's a new artifact.)
        if (hasAndroidVersion() && !getAndroidVersion().equals(existingDesc.getAndroidVersion())) {
            return false;
        }

        // Packages that have a vendor id need the same vendor id on both sides
        if (hasVendorId() && !getVendorId().equals(existingDesc.getVendorId())) {
            return false;
        }

        // Packages that have a tag id need the same tag id on both sides
        if (hasTag() && !getTag().getId().equals(existingDesc.getTag().getId())) {
            return false;
        }

        // Packages that have a path can generally only be updated if both use the same path
        if (hasPath()) {
            if (this instanceof IPkgDescExtra) {
                // Extra package handle paths differently, they need to use the old_path
                // to allow upgrade compatibility.
                if (!PkgDescExtra.compatibleVendorAndPath((IPkgDescExtra) this,
                                                          (IPkgDescExtra) existingDesc)) {
                    return false;
                }
            } else if (!getPath().equals(existingDesc.getPath())) {
                return false;
            }
        }

        // Packages that have a major version are generally updates if it increases.
        if (hasMajorRevision() &&
                getMajorRevision().compareTo(existingDesc.getMajorRevision()) > 0) {
            return true;
        }

        // Packages that have a full revision are generally updates if it increases
        // but keeps the same kind of preview (e.g. previews are only updates by previews.)
        if (hasFullRevision() &&
                getFullRevision().isPreview() == existingDesc.getFullRevision().isPreview()) {
            // If both packages match in their preview type (both previews or both not previews)
            // then is the RC/preview number an update?
            return getFullRevision().compareTo(existingDesc.getFullRevision(),
                                               PreviewComparison.COMPARE_NUMBER) > 0;
        }

        return false;
    }


    //---- Ordering ----

    /**
     * Compares this descriptor to another one.
     * All fields must match for equality.
     * <p/>
     * This is must not be used an indication that a package is a suitable update for another one.
     * The comparison order is however suitable for sorting packages for display purposes.
     */
    @Override
    public int compareTo(@NonNull IPkgDesc o) {
        int t1 = getType().getIntValue();
        int t2 = o.getType().getIntValue();
        if (t1 != t2) {
            return t1 - t2;
        }

        if (hasAndroidVersion() && o.hasAndroidVersion()) {
            t1 = getAndroidVersion().compareTo(o.getAndroidVersion());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasVendorId() && o.hasVendorId()) {
            t1 = getVendorId().compareTo(o.getVendorId());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasTag() && o.hasTag()) {
            t1 = getTag().compareTo(o.getTag());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasPath() && o.hasPath()) {
            t1 = getPath().compareTo(o.getPath());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasFullRevision() && o.hasFullRevision()) {
            t1 = getFullRevision().compareTo(o.getFullRevision());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasMajorRevision() && o.hasMajorRevision()) {
            t1 = getMajorRevision().compareTo(o.getMajorRevision());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasMinToolsRev() && o.hasMinToolsRev()) {
            t1 = getMinToolsRev().compareTo(o.getMinToolsRev());
            if (t1 != 0) {
                return t1;
            }
        }

        if (hasMinPlatformToolsRev() && o.hasMinPlatformToolsRev()) {
            t1 = getMinPlatformToolsRev().compareTo(o.getMinPlatformToolsRev());
            if (t1 != 0) {
                return t1;
            }
        }

        return 0;
    }

    /** String representation for debugging purposes. */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<PkgDesc");                                                 //NON-NLS-1$

        builder.append(" Type=");                                                   //NON-NLS-1$
        builder.append(getType().toString()
                                .toLowerCase(Locale.US)
                                .replace("pkg_", ""));                 //NON-NLS-1$ //NON-NLS-2$

        if (hasAndroidVersion()) {
            builder.append(" Android=").append(getAndroidVersion());                //NON-NLS-1$
        }

        if (hasVendorId()) {
            builder.append(" Vendor=").append(getVendorId());                       //NON-NLS-1$
        }

        if (hasTag()) {
            builder.append(" Tag=").append(getTag());                               //NON-NLS-1$
        }

        if (hasPath()) {
            builder.append(" Path=").append(getPath());                             //NON-NLS-1$
        }

        if (hasFullRevision()) {
            builder.append(" FullRev=").append(getFullRevision());                  //NON-NLS-1$
        }

        if (hasMajorRevision()) {
            builder.append(" MajorRev=").append(getMajorRevision());                //NON-NLS-1$
        }

        if (hasMinToolsRev()) {
            builder.append(" MinToolsRev=").append(getMinToolsRev());               //NON-NLS-1$
        }

        if (hasMinPlatformToolsRev()) {
            builder.append(" MinPlatToolsRev=").append(getMinPlatformToolsRev());   //NON-NLS-1$
        }

        builder.append('>');
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (hasAndroidVersion() ? getAndroidVersion().hashCode() : 0);
        result = prime * result + (hasVendorId()       ? getVendorId()      .hashCode() : 0);
        result = prime * result + (hasTag()            ? getTag()           .hashCode() : 0);
        result = prime * result + (hasPath()           ? getPath()          .hashCode() : 0);
        result = prime * result + (hasFullRevision()   ? getFullRevision()  .hashCode() : 0);
        result = prime * result + (hasMajorRevision()  ? getMajorRevision() .hashCode() : 0);
        result = prime * result + (hasMinToolsRev()    ? getMinToolsRev()   .hashCode() : 0);
        result = prime * result + (hasMinPlatformToolsRev() ?
                                                         getMinPlatformToolsRev().hashCode() : 0);

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IPkgDesc)) return false;
        IPkgDesc rhs = (IPkgDesc) obj;

        if (hasAndroidVersion() != rhs.hasAndroidVersion()) {
            return false;
        }
        if (hasAndroidVersion() && !getAndroidVersion().equals(rhs.getAndroidVersion())) {
            return false;
        }

        if (hasTag() != rhs.hasTag()) {
            return false;
        }
        if (hasTag() && !getTag().equals(rhs.getTag())) {
            return false;
        }

        if (hasPath() != rhs.hasPath()) {
            return false;
        }
        if (hasPath() && !getPath().equals(rhs.getPath())) {
            return false;
        }

        if (hasFullRevision() != rhs.hasFullRevision()) {
            return false;
        }
        if (hasFullRevision() && !getFullRevision().equals(rhs.getFullRevision())) {
            return false;
        }

        if (hasMajorRevision() != rhs.hasMajorRevision()) {
            return false;
        }
        if (hasMajorRevision() && !getMajorRevision().equals(rhs.getMajorRevision())) {
            return false;
        }

        if (hasMinToolsRev() != rhs.hasMinToolsRev()) {
            return false;
        }
        if (hasMinToolsRev() && !getMinToolsRev().equals(rhs.getMinToolsRev())) {
            return false;
        }

        if (hasMinPlatformToolsRev() != rhs.hasMinPlatformToolsRev()) {
            return false;
        }
        if (hasMinPlatformToolsRev() &&
                !getMinPlatformToolsRev().equals(rhs.getMinPlatformToolsRev())) {
            return false;
        }

        return true;
    }


    // ---- Constructors -----

    public interface IIsUpdateFor {
        public boolean isUpdateFor(@NonNull PkgDesc thisPkgDesc, @NonNull IPkgDesc existingDesc);
    }

    public interface IGetPath {
        public String getPath(@NonNull PkgDesc thisPkgDesc);
    }

    public static class Builder {
        private final PkgType mType;
        private FullRevision mFullRevision;
        private MajorRevision mMajorRevision;
        private AndroidVersion mAndroidVersion;
        private String mPath;
        private IdDisplay mTag;
        private String mVendorId;
        private FullRevision mMinToolsRev;
        private FullRevision mMinPlatformToolsRev;
        private IIsUpdateFor mCustomIsUpdateFor;
        private IGetPath mCustomPath;
        private String[] mOldPaths;
        private String mAddonVendor;
        private String mAddonName;
        private IAddonDesc mTargetHashProvider;


        private Builder(PkgType type) {
            mType = type;
        }

        /**
         * Creates a new tool package descriptor.
         *
         * @param revision The revision of the tool package.
         * @param minPlatformToolsRev The {@code min-platform-tools-rev}.
         *                  Use {@link FullRevision#NOT_SPECIFIED} to indicate there is no requirement.
         * @return A {@link PkgDesc} describing this tool package.
         */
        @NonNull
        public static Builder newTool(@NonNull FullRevision revision,
                                      @NonNull FullRevision minPlatformToolsRev) {
            Builder p = new Builder(PkgType.PKG_TOOLS);
            p.mFullRevision = revision;
            p.mMinPlatformToolsRev = minPlatformToolsRev;
            return p;
        }

        /**
         * Creates a new platform-tool package descriptor.
         *
         * @param revision The revision of the platform-tool package.
         * @return A {@link PkgDesc} describing this platform-tool package.
         */
        @NonNull
        public static Builder newPlatformTool(@NonNull FullRevision revision) {
            Builder p = new Builder(PkgType.PKG_PLATFORM_TOOLS);
            p.mFullRevision = revision;
            return p;
        }

        /**
         * Creates a new build-tool package descriptor.
         *
         * @param revision The revision of the build-tool package.
         * @return A {@link PkgDesc} describing this build-tool package.
         */
        @NonNull
        public static Builder newBuildTool(@NonNull FullRevision revision) {
            Builder p = new Builder(PkgType.PKG_BUILD_TOOLS);
            p.mFullRevision = revision;
            p.mCustomIsUpdateFor = new IIsUpdateFor() {
                @Override
                public boolean isUpdateFor(PkgDesc thisPkgDesc, IPkgDesc existingDesc) {
                    // Generic test checks that the preview type is the same (both previews or not).
                    // Build tool is different in that the full revision must be an exact match
                    // and not an increase.
                    return thisPkgDesc.isGenericUpdateFor(existingDesc) &&
                        thisPkgDesc.getFullRevision().compareTo(
                                            existingDesc.getFullRevision(),
                                           PreviewComparison.COMPARE_TYPE) == 0;
                }
            };
            return p;
        }

        /**
         * Creates a new doc package descriptor.
         *
         * @param revision The revision of the doc package.
         * @return A {@link PkgDesc} describing this doc package.
         */
        @NonNull
        public static Builder newDoc(@NonNull AndroidVersion version,
                                     @NonNull MajorRevision revision) {
            Builder p = new Builder(PkgType.PKG_DOCS);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mCustomIsUpdateFor = new IIsUpdateFor() {
                @Override
                public boolean isUpdateFor(PkgDesc thisPkgDesc, IPkgDesc existingDesc) {
                    if (existingDesc == null ||
                            !thisPkgDesc.getType().equals(existingDesc.getType())) {
                        return false;
                    }

                    // This package is unique in the SDK. It's an update if the API is newer
                    // or the revision is newer for the same API.
                    int diff = thisPkgDesc.getAndroidVersion().compareTo(
                              existingDesc.getAndroidVersion());
                    return diff > 0 ||
                           (diff == 0 && thisPkgDesc.getMajorRevision().compareTo(
                                        existingDesc.getMajorRevision()) > 0);
                }
            };
            return p;
        }

        /**
         * Creates a new extra package descriptor.
         *
         * @param vendorId The vendor id string of the extra package.
         * @param path The path id string of the extra package.
         * @param oldPaths An optional list of older paths for this extra package.
         * @param revision The revision of the extra package.
         * @return A {@link PkgDesc} describing this extra package.
         */
        @NonNull
        public static Builder newExtra(@NonNull  String vendorId,
                                       @NonNull  String path,
                                       @Nullable String[] oldPaths,
                                       @NonNull  NoPreviewRevision revision) {
            Builder p = new Builder(PkgType.PKG_EXTRAS);
            p.mFullRevision = revision;
            p.mVendorId = vendorId;
            p.mPath = path;
            p.mOldPaths = oldPaths;
            return p;
        }

        /**
         * Creates a new platform package descriptor.
         *
         * @param version The android version of the platform package.
         * @param revision The revision of the extra package.
         * @param minToolsRev An optional {@code min-tools-rev}.
         *                    Use {@link FullRevision#NOT_SPECIFIED} to indicate
         *                    there is no requirement.
         * @return A {@link PkgDesc} describing this platform package.
         */
        @NonNull
        public static Builder newPlatform(@NonNull AndroidVersion version,
                                          @NonNull MajorRevision revision,
                                          @NonNull FullRevision minToolsRev) {
            Builder p = new Builder(PkgType.PKG_PLATFORMS);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mMinToolsRev = minToolsRev;
            p.mCustomPath = new IGetPath() {
                @Override
                public String getPath(PkgDesc thisPkgDesc) {
                    /** The "path" of a Platform is its Target Hash. */
                    return AndroidTargetHash.getPlatformHashString(thisPkgDesc.getAndroidVersion());
                }
            };
            return p;
        }

        /**
         * Create a new add-on package descriptor.
         * <p/>
         * The vendor id and the name id provided are used to compute the add-on's
         * target hash.
         *
         * @param version The android version of the add-on package.
         * @param revision The revision of the add-on package.
         * @param addonVendor The vendor id of the add-on package.
         * @param addonName The name id of the add-on package.
         * @return A {@link PkgDesc} describing this add-on package.
         */
        @NonNull
        public static Builder newAddon(@NonNull AndroidVersion version,
                                       @NonNull MajorRevision revision,
                                       @NonNull String addonVendor,
                                       @NonNull String addonName) {
            Builder p = new Builder(PkgType.PKG_ADDONS);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mAddonVendor = addonVendor;
            p.mAddonName = addonName;
            return p;
        }

        /**
         * Create a new platform add-on descriptor where the target hash isn't determined yet.
         *
         * @param version The android version of the add-on package.
         * @param revision The revision of the add-on package.
         * @param targetHashProvider Implements a method that will return the target hash when needed.
         * @return A {@link PkgDesc} describing this add-on package.
         */
        @NonNull
        public static Builder newAddon(@NonNull AndroidVersion version,
                                       @NonNull MajorRevision revision,
                                       @NonNull IAddonDesc targetHashProvider) {
            Builder p = new Builder(PkgType.PKG_ADDONS);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mTargetHashProvider = targetHashProvider;
            return p;
        }

        /**
         * Create a new system-image package descriptor.
         * <p/>
         * For system-images, {@link PkgDesc#getPath()} returns the ABI.
         *
         * @param version The android version of the system-image package.
         * @param tag The tag of the system-image package.
         * @param abi The ABI of the system-image package.
         * @param revision The revision of the system-image package.
         * @return A {@link PkgDesc} describing this system-image package.
         */
        @NonNull
        public static Builder newSysImg(@NonNull AndroidVersion version,
                                        @NonNull IdDisplay tag,
                                        @NonNull String abi,
                                        @NonNull MajorRevision revision) {
            Builder p = new Builder(PkgType.PKG_SYS_IMAGES);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mTag = tag;
            p.mPath = abi;
            return p;
        }

        /**
         * Create a new source package descriptor.
         *
         * @param version The android version of the source package.
         * @param revision The revision of the source package.
         * @return A {@link PkgDesc} describing this source package.
         */
        @NonNull
        public static Builder newSource(@NonNull AndroidVersion version,
                                        @NonNull MajorRevision revision) {
            Builder p = new Builder(PkgType.PKG_SOURCES);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            return p;
        }

        /**
         * Create a new sample package descriptor.
         *
         * @param version The android version of the sample package.
         * @param revision The revision of the sample package.
         * @param minToolsRev An optional {@code min-tools-rev}.
         *                    Use {@link FullRevision#NOT_SPECIFIED} to indicate
         *                    there is no requirement.
         * @return A {@link PkgDesc} describing this sample package.
         */
        @NonNull
        public static Builder newSample(@NonNull AndroidVersion version,
                                        @NonNull MajorRevision revision,
                                        @NonNull FullRevision minToolsRev) {
            Builder p = new Builder(PkgType.PKG_SAMPLES);
            p.mAndroidVersion = version;
            p.mMajorRevision = revision;
            p.mMinToolsRev = minToolsRev;
            return p;
        }

        public IPkgDesc create() {
            if (mType == PkgType.PKG_ADDONS) {
                return new PkgDescAddon(
                        mType,
                        mFullRevision,
                        mMajorRevision,
                        mAndroidVersion,
                        mTag,
                        mVendorId,
                        mMinToolsRev,
                        mMinPlatformToolsRev,
                        mAddonVendor,
                        mAddonName,
                        mTargetHashProvider);
            }

            if (mType == PkgType.PKG_EXTRAS) {
                return new PkgDescExtra(
                    mType,
                    mFullRevision,
                    mMajorRevision,
                    mAndroidVersion,
                    mPath,
                    mTag,
                    mVendorId,
                    mMinToolsRev,
                    mMinPlatformToolsRev,
                    mOldPaths);
            }

            return new PkgDesc(
                    mType,
                    mFullRevision,
                    mMajorRevision,
                    mAndroidVersion,
                    mPath,
                    mTag,
                    mVendorId,
                    mMinToolsRev,
                    mMinPlatformToolsRev,
                    mCustomIsUpdateFor,
                    mCustomPath);
        }
    }
}

