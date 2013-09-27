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

package com.android.sdklib.local;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SdkManager.LayoutlibVersion;
import com.android.sdklib.SystemImage;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.internal.repository.packages.PlatformPackage;
import com.android.sdklib.io.IFileOp;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("ConstantConditions")
public class LocalPlatformPkgInfo extends LocalAndroidVersionPkgInfo {

    public static final String PROP_VERSION_SDK      = "ro.build.version.sdk";      //$NON-NLS-1$
    public static final String PROP_VERSION_CODENAME = "ro.build.version.codename"; //$NON-NLS-1$
    public static final String PROP_VERSION_RELEASE  = "ro.build.version.release";  //$NON-NLS-1$

    private final MajorRevision mRevision;
    /** Android target, lazyly loaded from #getAndroidTarget */
    private IAndroidTarget mTarget;
    private boolean mLoaded;

    public LocalPlatformPkgInfo(@NonNull LocalSdk localSdk,
                                @NonNull File localDir,
                                @NonNull Properties sourceProps,
                                @NonNull AndroidVersion version,
                                @NonNull MajorRevision revision) {
        super(localSdk, localDir, sourceProps, version);
        mRevision = revision;
    }

    @Override
    public int getType() {
        return LocalSdk.PKG_PLATFORMS;
    }

    @Override
    public boolean hasPath() {
        return true;
    }

    @Override
    public String getPath() {
        return getTargetHash();
    }

    @NonNull
    public String getTargetHash() {
        return AndroidTargetHash.getPlatformHashString(getAndroidVersion());
    }

    @Nullable
    public IAndroidTarget getAndroidTarget() {
        if (!mLoaded) {
            mTarget = createAndroidTarget();
            mLoaded = true;
        }
        return mTarget;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    @Override
    public boolean hasMajorRevision() {
        return true;
    }

    @Override
    public MajorRevision getMajorRevision() {
        return mRevision;
    }

    @Override
    public Package getPackage() {
        Package pkg = super.getPackage();
        if (pkg != null) {
            return pkg;
        }
        pkg = createPackage();
        setPackage(pkg);
        return pkg;
    }

    //-----

    /**
     * Creates a PlatformPackage wrapping the IAndroidTarget if defined.
     * Invoked by {@link #getPackage()}.
     *
     * @return A Package or null if target isn't available.
     */
    @Nullable
    protected Package createPackage() {
        IAndroidTarget target = getAndroidTarget();
        if (target != null) {
            return PlatformPackage.create(target, getSourceProperties());
        }
        return null;
    }

    /**
     * Creates the PlatformTarget. Invoked by {@link #getAndroidTarget()}.
     */
    @SuppressWarnings("ConstantConditions")
    @Nullable
    protected IAndroidTarget createAndroidTarget() {
        LocalSdk sdk = getLocalSdk();
        IFileOp fileOp = sdk.getFileOp();
        File platformFolder = getLocalDir();
        File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
        File sourcePropFile = new File(platformFolder, SdkConstants.FN_SOURCE_PROP);

        if (!fileOp.isFile(buildProp) || !fileOp.isFile(sourcePropFile)) {
            appendLoadError("Ignoring platform '%1$s': %2$s is missing.",   //$NON-NLS-1$
                    platformFolder.getName(),
                    SdkConstants.FN_BUILD_PROP);
            return null;
        }

        Map<String, String> platformProp = new HashMap<String, String>();

        // add all the property files
        Map<String, String> map = null;

        try {
            map = ProjectProperties.parsePropertyStream(
                    fileOp.newFileInputStream(buildProp),
                    buildProp.getPath(),
                    null /*log*/);
            if (map != null) {
                platformProp.putAll(map);
            }
        } catch (FileNotFoundException ignore) {}

        try {
            map = ProjectProperties.parsePropertyStream(
                    fileOp.newFileInputStream(sourcePropFile),
                    sourcePropFile.getPath(),
                    null /*log*/);
            if (map != null) {
                platformProp.putAll(map);
            }
        } catch (FileNotFoundException ignore) {}

        File sdkPropFile = new File(platformFolder, SdkConstants.FN_SDK_PROP);
        if (fileOp.isFile(sdkPropFile)) { // obsolete platforms don't have this.
            try {
                map = ProjectProperties.parsePropertyStream(
                        fileOp.newFileInputStream(sdkPropFile),
                        sdkPropFile.getPath(),
                        null /*log*/);
                if (map != null) {
                    platformProp.putAll(map);
                }
            } catch (FileNotFoundException ignore) {}
        }

        // look for some specific values in the map.

        // api level
        int apiNumber;
        String stringValue = platformProp.get(PROP_VERSION_SDK);
        if (stringValue == null) {
            appendLoadError("Ignoring platform '%1$s': %2$s is missing from '%3$s'",
                    platformFolder.getName(), PROP_VERSION_SDK,
                    SdkConstants.FN_BUILD_PROP);
            return null;
        } else {
            try {
                 apiNumber = Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                // looks like apiNumber does not parse to a number.
                // Ignore this platform.
                appendLoadError(
                        "Ignoring platform '%1$s': %2$s is not a valid number in %3$s.",
                        platformFolder.getName(), PROP_VERSION_SDK,
                        SdkConstants.FN_BUILD_PROP);
                return null;
            }
        }

        // Codename must be either null or a platform codename.
        // REL means it's a release version and therefore the codename should be null.
        AndroidVersion apiVersion =
            new AndroidVersion(apiNumber, platformProp.get(PROP_VERSION_CODENAME));

        // version string
        String apiName = platformProp.get(PkgProps.PLATFORM_VERSION);
        if (apiName == null) {
            apiName = platformProp.get(PROP_VERSION_RELEASE);
        }
        if (apiName == null) {
            appendLoadError(
                    "Ignoring platform '%1$s': %2$s is missing from '%3$s'",
                    platformFolder.getName(), PROP_VERSION_RELEASE,
                    SdkConstants.FN_BUILD_PROP);
            return null;
        }

        // platform rev number & layoutlib version are extracted from the source.properties
        // saved by the SDK Manager when installing the package.

        int revision = 1;
        LayoutlibVersion layoutlibVersion = null;
        try {
            revision = Integer.parseInt(platformProp.get(PkgProps.PKG_REVISION));
        } catch (NumberFormatException e) {
            // do nothing, we'll keep the default value of 1.
        }

        try {
            String propApi = platformProp.get(PkgProps.LAYOUTLIB_API);
            String propRev = platformProp.get(PkgProps.LAYOUTLIB_REV);
            int llApi = propApi == null ? LayoutlibVersion.NOT_SPECIFIED :
                                          Integer.parseInt(propApi);
            int llRev = propRev == null ? LayoutlibVersion.NOT_SPECIFIED :
                                          Integer.parseInt(propRev);
            if (llApi > LayoutlibVersion.NOT_SPECIFIED &&
                    llRev >= LayoutlibVersion.NOT_SPECIFIED) {
                layoutlibVersion = new LayoutlibVersion(llApi, llRev);
            }
        } catch (NumberFormatException e) {
            // do nothing, we'll ignore the layoutlib version if it's invalid
        }

        // api number and name look valid, perform a few more checks
        String err = checkPlatformContent(fileOp, platformFolder);
        if (err != null) {
            appendLoadError("%s", err); //$NLN-NLS-1$
            return null;
        }

        ISystemImage[] systemImages = getPlatformSystemImages(fileOp, platformFolder, apiVersion);

        // create the target.
        PlatformTarget pt = new PlatformTarget(
                sdk.getLocation().getPath(),
                platformFolder.getAbsolutePath(),
                apiVersion,
                apiName,
                revision,
                layoutlibVersion,
                systemImages,
                platformProp,
                sdk.getLatestBuildTool());

        // add the skins.
        String[] skins = parseSkinFolder(pt.getPath(IAndroidTarget.SKINS));
        pt.setSkins(skins);

        // add path to the non-legacy samples package if it exists
        LocalPkgInfo samples = sdk.getPkgInfo(LocalSdk.PKG_SAMPLES, getAndroidVersion());
        if (samples != null) {
            pt.setSamplesPath(samples.getLocalDir().getAbsolutePath());
        }

        // add path to the non-legacy sources package if it exists
        LocalPkgInfo sources = sdk.getPkgInfo(LocalSdk.PKG_SOURCES, getAndroidVersion());
        if (sources != null) {
            pt.setSourcesPath(sources.getLocalDir().getAbsolutePath());
        }

        return pt;
    }

    /**
     * Get all the system images supported by a platform target.
     * For a platform, we first look in the new sdk/system-images folders then we
     * look for sub-folders in the platform/images directory and/or the one legacy
     * folder.
     * If any given API appears twice or more, the first occurrence wins.
     *
     * @param fileOp File operation wrapper.
     * @param platformDir Root of the platform target being loaded.
     * @param apiVersion API level + codename of platform being loaded.
     * @return an array of ISystemImage containing all the system images for the target.
     *              The list can be empty but not null.
     */
    @NonNull
    private ISystemImage[] getPlatformSystemImages(IFileOp fileOp,
                                                   File platformDir,
                                                   AndroidVersion apiVersion) {
        Set<ISystemImage> found = new TreeSet<ISystemImage>();
        Set<String> abiFound = new HashSet<String>();

        // First look in the SDK/system-image/platform-n/abi folders.
        // If we find multiple occurrences of the same platform/abi, the first one read wins.

        for (LocalPkgInfo pkg : getLocalSdk().getPkgsInfos(LocalSdk.PKG_SYS_IMAGES)) {
            if (pkg instanceof LocalSysImgPkgInfo && apiVersion.equals(pkg.getAndroidVersion())) {
                String abi = ((LocalSysImgPkgInfo)pkg).getAbi();
                if (abi != null && !abiFound.contains(abi)) {
                    found.add(new SystemImage(
                            pkg.getLocalDir(),
                            LocationType.IN_SYSTEM_IMAGE,
                            abi));
                    abiFound.add(abi);
                }
            }
        }

        // Then look in either the platform/images/abi or the legacy folder
        File imgDir = new File(platformDir, SdkConstants.OS_IMAGES_FOLDER);
        File[] files =  fileOp.listFiles(imgDir);
        boolean useLegacy = true;
        boolean hasImgFiles = false;

        // Look for sub-directories
        for (File file : files) {
            if (fileOp.isDirectory(file)) {
                useLegacy = false;
                String abi = file.getName();
                if (!abiFound.contains(abi)) {
                    found.add(new SystemImage(
                            file,
                            LocationType.IN_PLATFORM_SUBFOLDER,
                            abi));
                    abiFound.add(abi);
                }
            } else if (!hasImgFiles && fileOp.isFile(file)) {
                if (file.getName().endsWith(".img")) {      //$NON-NLS-1$
                    hasImgFiles = true;
                }
            }
        }

        if (useLegacy &&
                hasImgFiles &&
                fileOp.isDirectory(imgDir) &&
                !abiFound.contains(SdkConstants.ABI_ARMEABI)) {
            // We found no sub-folder system images but it looks like the top directory
            // has some img files in it. It must be a legacy ARM EABI system image folder.
            found.add(new SystemImage(
                    imgDir,
                    LocationType.IN_PLATFORM_LEGACY,
                    SdkConstants.ABI_ARMEABI));
        }

        return found.toArray(new ISystemImage[found.size()]);
    }

    /**
     * Parses the skin folder and builds the skin list.
     * @param osPath The path of the skin root folder.
     */
    @NonNull
    protected String[] parseSkinFolder(@NonNull String osPath) {
        IFileOp fileOp = getLocalSdk().getFileOp();
        File skinRootFolder = new File(osPath);

        if (fileOp.isDirectory(skinRootFolder)) {
            ArrayList<String> skinList = new ArrayList<String>();

            File[] files = fileOp.listFiles(skinRootFolder);

            for (File skinFolder : files) {
                if (fileOp.isDirectory(skinFolder)) {
                    // check for layout file
                    File layout = new File(skinFolder, SdkConstants.FN_SKIN_LAYOUT);

                    if (fileOp.isFile(layout)) {
                        // for now we don't parse the content of the layout and
                        // simply add the directory to the list.
                        skinList.add(skinFolder.getName());
                    }
                }
            }

            return skinList.toArray(new String[skinList.size()]);
        }

        return new String[0];
    }


    /** List of items in the platform to check when parsing it. These paths are relative to the
     * platform root folder. */
    private static final String[] sPlatformContentList = new String[] {
        SdkConstants.FN_FRAMEWORK_LIBRARY,
        SdkConstants.FN_FRAMEWORK_AIDL,
    };

    /**
     * Checks the given platform has all the required files, and returns true if they are all
     * present.
     * <p/>This checks the presence of the following files: android.jar, framework.aidl, aapt(.exe),
     * aidl(.exe), dx(.bat), and dx.jar
     *
     * @param fileOp File operation wrapper.
     * @param platform The folder containing the platform.
     * @return An error description if platform is rejected; null if no error is detected.
     */
    @NonNull
    private static String checkPlatformContent(IFileOp fileOp, @NonNull File platform) {
        for (String relativePath : sPlatformContentList) {
            File f = new File(platform, relativePath);
            if (!fileOp.exists(f)) {
                return String.format(
                        "Ignoring platform '%1$s': %2$s is missing.",                  //$NON-NLS-1$
                        platform.getName(), relativePath);
            }
        }
        return null;
    }
}
