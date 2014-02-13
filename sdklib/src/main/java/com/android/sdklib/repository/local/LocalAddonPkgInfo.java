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

package com.android.sdklib.repository.local;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SystemImage;
import com.android.sdklib.internal.androidTarget.AddOnTarget;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.repository.packages.AddonPackage;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.io.IFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IAddonDesc;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.utils.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("MethodMayBeStatic")
public class LocalAddonPkgInfo extends LocalPlatformPkgInfo {

    public static final String ADDON_NAME         = "name";                 //$NON-NLS-1$
    public static final String ADDON_VENDOR       = "vendor";               //$NON-NLS-1$
    public static final String ADDON_API          = "api";                  //$NON-NLS-1$
    public static final String ADDON_DESCRIPTION  = "description";          //$NON-NLS-1$
    public static final String ADDON_LIBRARIES    = "libraries";            //$NON-NLS-1$
    public static final String ADDON_DEFAULT_SKIN = "skin";                 //$NON-NLS-1$
    public static final String ADDON_USB_VENDOR   = "usb-vendor";           //$NON-NLS-1$
    public static final String ADDON_REVISION     = "revision";             //$NON-NLS-1$
    public static final String ADDON_REVISION_OLD = "version";              //$NON-NLS-1$

    private static final Pattern PATTERN_LIB_DATA = Pattern.compile(
            "^([a-zA-Z0-9._-]+\\.jar);(.*)$", Pattern.CASE_INSENSITIVE);    //$NON-NLS-1$

    // usb ids are 16-bit hexadecimal values.
    private static final Pattern PATTERN_USB_IDS = Pattern.compile(
           "^0x[a-f0-9]{4}$", Pattern.CASE_INSENSITIVE);                    //$NON-NLS-1$

    private final @NonNull IPkgDesc mAddonDesc;
    private String mTargetHash;
    private String mVendorId;

    public LocalAddonPkgInfo(@NonNull LocalSdk localSdk,
                             @NonNull File localDir,
                             @NonNull Properties sourceProps,
                             @NonNull AndroidVersion version,
                             @NonNull MajorRevision revision) {
        super(localSdk, localDir, sourceProps, version, revision, FullRevision.NOT_SPECIFIED);
        mAddonDesc = PkgDesc.newAddon(version, revision, new IAddonDesc() {
            @NonNull
            @Override
            public String getTargetHash() {
                // Lazily compute the target hash the first time it is required.
                return LocalAddonPkgInfo.this.getTargetHash();
            }

            @NonNull
            @Override
            public String getVendorId() {
                // Lazily compute the vendor id the first time it is required.
                return LocalAddonPkgInfo.this.getVendorId();
            }
        });
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mAddonDesc;
    }

    @NonNull
    public String getVendorId() {
        if (mVendorId == null) {
            IAndroidTarget target = getAndroidTarget();

            String vendor = null;

            if (target != null) {
                vendor = target.getVendor();
            } else {
                Pair<Map<String, String>, String> infos = parseAddonProperties();
                Map<String, String> map = infos.getFirst();
                if (map != null) {
                    vendor = map.get(ADDON_VENDOR);
                }
            }

            if (vendor == null) {
                return "invalid";                                           //$NON-NLS-1$
            }

            mVendorId = vendor;
        }
        return mVendorId;
    }

    @NonNull
    @Override
    public String getTargetHash() {
        if (mTargetHash == null) {
            IAndroidTarget target = getAndroidTarget();

            String vendor = null;
            String name   = null;

            if (target != null) {
                vendor = target.getVendor();
                name   = target.getName();
            } else {
                Pair<Map<String, String>, String> infos = parseAddonProperties();
                Map<String, String> map = infos.getFirst();
                if (map != null) {
                    vendor = map.get(ADDON_VENDOR);
                    name   = map.get(ADDON_NAME);
                }
            }

            if (vendor == null) {
                vendor = mVendorId;
            }

            if (vendor == null || name == null) {
                return "invalid";                                       //$NON-NLS-1$
            }

            mVendorId = vendor;
            mTargetHash = AndroidTargetHash.getAddonHashString(
                    vendor,
                    name,
                    getDesc().getAndroidVersion());
        }
        return mTargetHash;
    }

    //-----

    /**
     * Creates an AddonPackage wrapping the IAndroidTarget if defined.
     * Invoked by {@link #getPackage()}.
     *
     * @return A Package or null if target isn't available.
     */
    @Override
    @Nullable
    protected Package createPackage() {
        IAndroidTarget target = getAndroidTarget();
        if (target != null) {
            return AddonPackage.create(target, getSourceProperties());
        }
        return null;
    }

    /**
     * Creates the AddOnTarget. Invoked by {@link #getAndroidTarget()}.
     */
    @Override
    @Nullable
    protected IAndroidTarget createAndroidTarget() {
        LocalSdk sdk = getLocalSdk();
        IFileOp fileOp = sdk.getFileOp();

        // Parse the addon properties to ensure we can load it.
        Pair<Map<String, String>, String> infos = parseAddonProperties();

        Map<String, String> propertyMap = infos.getFirst();
        String error = infos.getSecond();

        if (error != null) {
            appendLoadError("Ignoring add-on '%1$s': %2$s", getLocalDir().getName(), error);
            return null;
        }

        // Since error==null we're not supposed to encounter any issues loading this add-on.
        try {
            assert propertyMap != null;

            String api = propertyMap.get(ADDON_API);
            String name = propertyMap.get(ADDON_NAME);
            String vendor = propertyMap.get(ADDON_VENDOR);

            assert api != null;
            assert name != null;
            assert vendor != null;

            PlatformTarget baseTarget = null;

            // Look for a platform that has a matching api level or codename.
            LocalPkgInfo plat = sdk.getPkgInfo(PkgType.PKG_PLATFORMS,
                                               getDesc().getAndroidVersion());
            if (plat instanceof LocalPlatformPkgInfo) {
                baseTarget = (PlatformTarget) ((LocalPlatformPkgInfo) plat).getAndroidTarget();
            }
            assert baseTarget != null;

            // get the optional description
            String description = propertyMap.get(ADDON_DESCRIPTION);

            // get the add-on revision
            int revisionValue = 1;
            String revision = propertyMap.get(ADDON_REVISION);
            if (revision == null) {
                revision = propertyMap.get(ADDON_REVISION_OLD);
            }
            if (revision != null) {
                revisionValue = Integer.parseInt(revision);
            }

            // get the optional libraries
            String librariesValue = propertyMap.get(ADDON_LIBRARIES);
            Map<String, String[]> libMap = null;

            if (librariesValue != null) {
                librariesValue = librariesValue.trim();
                if (!librariesValue.isEmpty()) {
                    // split in the string into the libraries name
                    String[] libraries = librariesValue.split(";");     //$NON-NLS-1$
                    if (libraries.length > 0) {
                        libMap = new HashMap<String, String[]>();
                        for (String libName : libraries) {
                            libName = libName.trim();

                            // get the library data from the properties
                            String libData = propertyMap.get(libName);

                            if (libData != null) {
                                // split the jar file from the description
                                Matcher m = PATTERN_LIB_DATA.matcher(libData);
                                if (m.matches()) {
                                    libMap.put(libName, new String[] {
                                            m.group(1), m.group(2) });
                                } else {
                                    appendLoadError(
                                            "Ignoring library '%1$s', property value has wrong format\n\t%2$s",
                                            libName, libData);
                                }
                            } else {
                                appendLoadError(
                                        "Ignoring library '%1$s', missing property value",
                                        libName, libData);
                            }
                        }
                    }
                }
            }

            // get the abi list.
            ISystemImage[] systemImages = getAddonSystemImages();

            // check whether the add-on provides its own rendering info/library.
            boolean hasRenderingLibrary = false;
            boolean hasRenderingResources = false;

            File dataFolder = new File(getLocalDir(), SdkConstants.FD_DATA);
            if (fileOp.isDirectory(dataFolder)) {
                hasRenderingLibrary =
                    fileOp.isFile(new File(dataFolder, SdkConstants.FN_LAYOUTLIB_JAR));
                hasRenderingResources =
                    fileOp.isDirectory(new File(dataFolder, SdkConstants.FD_RES)) &&
                    fileOp.isDirectory(new File(dataFolder, SdkConstants.FD_FONTS));
            }

            AddOnTarget target = new AddOnTarget(
                    getLocalDir().getAbsolutePath(),
                    name,
                    vendor,
                    revisionValue,
                    description,
                    systemImages,
                    libMap,
                    hasRenderingLibrary,
                    hasRenderingResources,
                    baseTarget);

            // need to parse the skins.
            File targetSkinFolder = target.getFile(IAndroidTarget.SKINS);
            List<File> skins = parseSkinFolder(targetSkinFolder);

            // get the default skin
            File defaultSkin = null;
            String defaultSkinName = propertyMap.get(ADDON_DEFAULT_SKIN);
            if (defaultSkinName != null) {
                defaultSkin = new File(targetSkinFolder, defaultSkinName);
            } else {
                // No default skin name specified, use the first one from the addon
                // or the default from the platform.
                if (skins.size() == 1) {
                    defaultSkin = skins.get(0);
                } else {
                    defaultSkin = baseTarget.getDefaultSkin();
                }
            }

            // get the USB ID (if available)
            int usbVendorId = convertId(propertyMap.get(ADDON_USB_VENDOR));
            if (usbVendorId != IAndroidTarget.NO_USB_ID) {
                target.setUsbVendorId(usbVendorId);
            }

            target.setSkins(skins.toArray(new File[skins.size()]), defaultSkin);

            return target;

        } catch (Exception e) {
            appendLoadError("Ignoring add-on '%1$s': error %2$s.",
                    getLocalDir().getName(), e.toString());
        }

        return null;

    }

    /**
     * Parses the add-on properties and decodes any error that occurs when loading an addon.
     *
     * @return A pair with the property map and an error string. Both can be null but not at the
     *  same time. If a non-null error is present then the property map must be ignored. The error
     *  should be translatable as it might show up in the SdkManager UI.
     */
    @NonNull
    private Pair<Map<String, String>, String> parseAddonProperties() {
        Map<String, String> propertyMap = null;
        String error = null;

        IFileOp fileOp = getLocalSdk().getFileOp();
        File addOnManifest = new File(getLocalDir(), SdkConstants.FN_MANIFEST_INI);

        do {
            if (!fileOp.isFile(addOnManifest)) {
                error = String.format("File not found: %1$s", SdkConstants.FN_MANIFEST_INI);
                break;
            }

            try {
                propertyMap = ProjectProperties.parsePropertyStream(
                        fileOp.newFileInputStream(addOnManifest),
                        addOnManifest.getPath(),
                        null /*log*/);
                if (propertyMap == null) {
                    error = String.format("Failed to parse properties from %1$s",
                            SdkConstants.FN_MANIFEST_INI);
                    break;
                }
            } catch (FileNotFoundException ignore) {}
            assert propertyMap != null;

            // look for some specific values in the map.
            // we require name, vendor, and api
            String name = propertyMap.get(ADDON_NAME);
            if (name == null) {
                error = addonManifestWarning(ADDON_NAME);
                break;
            }

            String vendor = propertyMap.get(ADDON_VENDOR);
            if (vendor == null) {
                error = addonManifestWarning(ADDON_VENDOR);
                break;
            }

            String api = propertyMap.get(ADDON_API);
            if (api == null) {
                error = addonManifestWarning(ADDON_API);
                break;
            }

            // Look for a platform that has a matching api level or codename.
            IAndroidTarget baseTarget = null;
            LocalPkgInfo plat = getLocalSdk().getPkgInfo(PkgType.PKG_PLATFORMS,
                                                         getDesc().getAndroidVersion());
            if (plat instanceof LocalPlatformPkgInfo) {
                baseTarget = ((LocalPlatformPkgInfo) plat).getAndroidTarget();
            }

            if (baseTarget == null) {
                error = String.format("Unable to find base platform with API level '%1$s'", api);
                break;
            }

            // get the add-on revision
            String revision = propertyMap.get(ADDON_REVISION);
            if (revision == null) {
                revision = propertyMap.get(ADDON_REVISION_OLD);
            }
            if (revision != null) {
                try {
                    Integer.parseInt(revision);
                } catch (NumberFormatException e) {
                    // looks like revision does not parse to a number.
                    error = String.format("%1$s is not a valid number in %2$s.",
                            ADDON_REVISION, SdkConstants.FN_BUILD_PROP);
                    break;
                }
            }

        } while(false);

        return Pair.of(propertyMap, error);
    }

    /**
     * Prepares a warning about the addon being ignored due to a missing manifest value.
     * This string will show up in the SdkManager UI.
     *
     * @param valueName The missing manifest value, for display.
     */
    @NonNull
    private static String addonManifestWarning(@NonNull String valueName) {
        return String.format("'%1$s' is missing from %2$s.",
                valueName, SdkConstants.FN_MANIFEST_INI);
    }

    /**
     * Converts a string representation of an hexadecimal ID into an int.
     * @param value the string to convert.
     * @return the int value, or {@link IAndroidTarget#NO_USB_ID} if the conversion failed.
     */
    private int convertId(@Nullable String value) {
        if (value != null && !value.isEmpty()) {
            if (PATTERN_USB_IDS.matcher(value).matches()) {
                String v = value.substring(2);
                try {
                    return Integer.parseInt(v, 16);
                } catch (NumberFormatException e) {
                    // this shouldn't happen since we check the pattern above, but this is safer.
                    // the method will return 0 below.
                }
            }
        }

        return IAndroidTarget.NO_USB_ID;
    }

    /**
     * Get all the system images supported by an add-on target.
     * For an add-on, we first look for sub-folders in the addon/images directory.
     * If none are found but the directory exists and is not empty, assume it's a legacy
     * arm eabi system image.
     * <p/>
     * Note that it's OK for an add-on to have no system-images at all, since it can always
     * rely on the ones from its base platform.
     *
     * @return an array of ISystemImage containing all the system images for the target.
     *              The list can be empty but not null.
    */
    @NonNull
    private ISystemImage[] getAddonSystemImages() {
        Set<ISystemImage> found = new TreeSet<ISystemImage>();

        IFileOp fileOp = getLocalSdk().getFileOp();
        File imagesDir = new File(getLocalDir(), SdkConstants.OS_IMAGES_FOLDER);

        // Look for sub-directories
        boolean hasImgFiles = false;
        File[] files = fileOp.listFiles(imagesDir);
        for (File file : files) {
            if (fileOp.isDirectory(file)) {
                found.add(new SystemImage(file,
                                          LocationType.IN_PLATFORM_SUBFOLDER,
                                          SystemImage.DEFAULT_TAG,
                                          file.getName(),
                                          FileOp.EMPTY_FILE_ARRAY));
            } else if (!hasImgFiles && fileOp.isFile(file)) {
                if (file.getName().endsWith(".img")) {                  //$NON-NLS-1$
                    hasImgFiles = true;
                }
            }
        }

        if (found.isEmpty() && hasImgFiles && fileOp.isDirectory(imagesDir)) {
            // We found no sub-folder system images but it looks like the top directory
            // has some img files in it. It must be a legacy ARM EABI system image folder.
            found.add(new SystemImage(imagesDir,
                                      LocationType.IN_PLATFORM_LEGACY,
                                      SystemImage.DEFAULT_TAG,
                                      SdkConstants.ABI_ARMEABI,
                                      FileOp.EMPTY_FILE_ARRAY));
        }

        return found.toArray(new ISystemImage[found.size()]);
    }
}
