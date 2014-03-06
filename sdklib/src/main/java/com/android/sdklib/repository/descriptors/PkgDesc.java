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
public abstract class PkgDesc implements IPkgDesc {

    @NonNull
    @Override
    public abstract PkgType getType();

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
        return null;
    }

    @Nullable
    @Override
    public MajorRevision getMajorRevision() {
        return null;
    }

    @Nullable
    @Override
    public AndroidVersion getAndroidVersion() {
        return null;
    }

    @Nullable
    @Override
    public String getPath() {
        return null;
    }

    @Nullable
    @Override
    public IdDisplay getTag() {
        return null;
    }

    @Nullable
    @Override
    public String getVendorId() {
        return null;
    }

    @Nullable
    @Override
    public FullRevision getMinToolsRev() {
        return null;
    }

    @Nullable
    @Override
    public FullRevision getMinPlatformToolsRev() {
        return null;
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
    protected final boolean isGenericUpdateFor(@NonNull IPkgDesc existingDesc) {
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

    /**
     * Create a new tool package descriptor.
     *
     * @param revision The revision of the tool package.
     * @param minPlatformToolsRev The {@code min-platform-tools-rev}.
     *                  Use {@link FullRevision#NOT_SPECIFIED} to indicate there is no requirement.
     * @return A {@link PkgDesc} describing this tool package.
     */
    @NonNull
    public static IPkgDesc newTool(@NonNull final FullRevision revision,
                                   @NonNull final FullRevision minPlatformToolsRev) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_TOOLS;
            }

            @Override
            public FullRevision getFullRevision() {
                return revision;
            }

            @Override
            public FullRevision getMinPlatformToolsRev() {
                return minPlatformToolsRev;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                // Generic test checks that the preview type is the same (both previews or not)
                // and whether this is a better RC/preview than the existing one.
                return isGenericUpdateFor(existingDesc);
            }
        };
    }

    /**
     * Create a new platform-tool package descriptor.
     *
     * @param revision The revision of the platform-tool package.
     * @return A {@link PkgDesc} describing this platform-tool package.
     */
    @NonNull
    public static IPkgDesc newPlatformTool(@NonNull final FullRevision revision) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_PLATFORM_TOOLS;
            }

            @Override
            public FullRevision getFullRevision() {
                return revision;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                // Generic test checks that the preview type is the same (both previews or not)
                // and whether this is a better RC/preview than the existing one.
                return isGenericUpdateFor(existingDesc);
            }
        };
    }

    /**
     * Create a new build-tool package descriptor.
     *
     * @param revision The revision of the build-tool package.
     * @return A {@link PkgDesc} describing this build-tool package.
     */
    @NonNull
    public static IPkgDesc newBuildTool(@NonNull final FullRevision revision) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_BUILD_TOOLS;
            }

            @Override
            public FullRevision getFullRevision() {
                return revision;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                // Generic test checks that the preview type is the same (both previews or not).
                // Build tool is different in that the full revision must be an exact match
                // and not an increase.
                return isGenericUpdateFor(existingDesc) &&
                    revision.compareTo(existingDesc.getFullRevision(),
                                       PreviewComparison.COMPARE_TYPE) == 0;
            }
        };
    }

    /**
     * Create a new doc package descriptor.
     *
     * @param revision The revision of the doc package.
     * @return A {@link PkgDesc} describing this doc package.
     */
    @NonNull
    public static IPkgDesc newDoc(@NonNull final AndroidVersion version,
                                  @NonNull final MajorRevision revision) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_DOCS;
            }

            @Override
            public MajorRevision getMajorRevision() {
                return revision;
            }

            @Override
            public AndroidVersion getAndroidVersion() {
                return version;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                if (existingDesc == null || !getType().equals(existingDesc.getType())) {
                    return false;
                }

                // This package is unique in the SDK. It's an update if the API is newer
                // or the revision is newer for the same API.
                int diff = version.compareTo(existingDesc.getAndroidVersion());
                return diff > 0 ||
                       (diff == 0 && revision.compareTo(existingDesc.getMajorRevision()) > 0);
            }
        };
    }

    /**
     * Create a new extra package descriptor.
     *
     * @param vendorId The vendor id string of the extra package.
     * @param path The path id string of the extra package.
     * @param oldPaths An optional list of older paths for this extra package.
     * @param revision The revision of the extra package.
     * @return A {@link PkgDesc} describing this extra package.
     */
    @NonNull
    public static IPkgDescExtra newExtra(@NonNull  final String vendorId,
                                         @NonNull  final String path,
                                         @Nullable final String[] oldPaths,
                                         @NonNull  final NoPreviewRevision revision) {
        return new PkgDescExtra(vendorId, path, oldPaths, revision);
    }

    /**
     * Create a new platform package descriptor.
     *
     * @param version The android version of the platform package.
     * @param revision The revision of the extra package.
     * @param minToolsRev An optional {@code min-tools-rev}.
     *                    Use {@link FullRevision#NOT_SPECIFIED} to indicate
     *                    there is no requirement.
     * @return A {@link PkgDesc} describing this platform package.
     */
    @NonNull
    public static IPkgDesc newPlatform(@NonNull final AndroidVersion version,
                                       @NonNull final MajorRevision revision,
                                       @NonNull final FullRevision minToolsRev) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_PLATFORMS;
            }

            @Override
            public MajorRevision getMajorRevision() {
                return revision;
            }

            @Override
            public AndroidVersion getAndroidVersion() {
                return version;
            }

            @Override
            public FullRevision getMinToolsRev() {
                return minToolsRev;
            }

            /** The "path" of a Platform is its Target Hash. */
            @Override
            public String getPath() {
                return AndroidTargetHash.getPlatformHashString(getAndroidVersion());
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                return isGenericUpdateFor(existingDesc);
            }
        };
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
    public static IPkgDesc newAddon(@NonNull AndroidVersion version,
                                    @NonNull MajorRevision revision,
                                    @NonNull String addonVendor,
                                    @NonNull String addonName) {
        return new PkgDescAddon(version, revision, addonVendor, addonName);
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
    public static IPkgDesc newAddon(@NonNull AndroidVersion version,
                                    @NonNull MajorRevision revision,
                                    @NonNull IAddonDesc targetHashProvider) {
        return new PkgDescAddon(version, revision, targetHashProvider);
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
    public static IPkgDesc newSysImg(@NonNull final AndroidVersion version,
                                     @NonNull final IdDisplay tag,
                                     @NonNull final String abi,
                                     @NonNull final MajorRevision revision) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_SYS_IMAGES;
            }

            @Override
            public MajorRevision getMajorRevision() {
                return revision;
            }

            @Override
            public AndroidVersion getAndroidVersion() {
                return version;
            }

            @Override
            public IdDisplay getTag() {
                return tag;
            }

            @Override
            public String getPath() {
                return abi;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                return isGenericUpdateFor(existingDesc);
            }
        };
    }

    /**
     * Create a new source package descriptor.
     *
     * @param version The android version of the source package.
     * @param revision The revision of the source package.
     * @return A {@link PkgDesc} describing this source package.
     */
    @NonNull
    public static IPkgDesc newSource(@NonNull final AndroidVersion version,
                                     @NonNull final MajorRevision revision) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_SOURCES;
            }

            @Override
            public MajorRevision getMajorRevision() {
                return revision;
            }

            @Override
            public AndroidVersion getAndroidVersion() {
                return version;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                return isGenericUpdateFor(existingDesc);
            }
        };
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
    public static IPkgDesc newSample(@NonNull final AndroidVersion version,
                                     @NonNull final MajorRevision revision,
                                     @NonNull final FullRevision minToolsRev) {
        return new PkgDesc() {
            @Override
            public PkgType getType() {
                return PkgType.PKG_SAMPLES;
            }

            @Override
            public MajorRevision getMajorRevision() {
                return revision;
            }

            @Override
            public AndroidVersion getAndroidVersion() {
                return version;
            }

            @Override
            public FullRevision getMinToolsRev() {
                return minToolsRev;
            }

            @Override
            public boolean isUpdateFor(@NonNull IPkgDesc existingDesc) {
                return isGenericUpdateFor(existingDesc);
            }
        };
    }
}

