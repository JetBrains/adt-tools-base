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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.packages.PackageParserUtils;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.io.IFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.Adler32;

/**
 * This class keeps information on the current locally installed SDK.
 * It tries to lazily load information as much as possible.
 * <p/>
 * Packages are accessed by their type and a main query attribute, depending on the
 * package type. There are different versions of {@link #getPkgInfo} which depend on the
 * query attribute.
 *
 * <table border='1' cellpadding='3'>
 * <tr>
 * <th>Type</th>
 * <th>Query parameter</th>
 * <th>Getter</th>
 * </tr>
 *
 * <tr>
 * <td>Tools</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_TOOLS)} => {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Platform-Tools</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_PLATFORM_TOOLS)} => {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Docs</td>
 * <td>Unique instance</td>
 * <td>{@code getPkgInfo(PkgType.PKG_DOCS)} => {@link LocalPkgInfo}</td>
 * </tr>
 *
 * <tr>
 * <td>Build-Tools</td>
 * <td>{@link FullRevision}</td>
 * <td>{@code getLatestBuildTool()} => {@link BuildToolInfo}, <br/>
 *     or {@code getBuildTool(FullRevision)} => {@link BuildToolInfo}, <br/>
 *     or {@code getPkgInfo(PkgType.PKG_BUILD_TOOLS, FullRevision)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_BUILD_TOOLS)} => {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Extras</td>
 * <td>String vendor/path</td>
 * <td>{@code getExtra(String)} => {@link LocalExtraPkgInfo}, <br/>
 *     or {@code getPkgInfo(PkgType.PKG_EXTRAS, String)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_EXTRAS)} => {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Sources</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_SOURCES, AndroidVersion)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_SOURCES)} => {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Samples</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_SAMPLES, AndroidVersion)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_SAMPLES)} => {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * <tr>
 * <td>Platforms</td>
 * <td>{@link AndroidVersion}</td>
 * <td>{@code getPkgInfo(PkgType.PKG_PLATFORMS, AndroidVersion)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgInfo(PkgType.PKG_ADDONS, String)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_PLATFORMS)} => {@link LocalPkgInfo}[], <br/>
 *     or {@code getTargetFromHashString(String)} => {@link IAndroidTarget}</td>
 * </tr>
 *
 * <tr>
 * <td>Add-ons</td>
 * <td>{@link AndroidVersion} x String vendor/path</td>
 * <td>{@code getPkgInfo(PkgType.PKG_ADDONS, String)} => {@link LocalPkgInfo}, <br/>
 *     or {@code getPkgsInfos(PkgType.PKG_ADDONS)}    => {@link LocalPkgInfo}[], <br/>
 *     or {@code getTargetFromHashString(String)} => {@link IAndroidTarget}</td>
 * </tr>
 *
 * <tr>
 * <td>System images</td>
 * <td>{@link AndroidVersion} x {@link String} ABI</td>
 * <td>{@code getPkgsInfos(PkgType.PKG_SYS_IMAGES)} => {@link LocalPkgInfo}[]</td>
 * </tr>
 *
 * </table>
 *
 * Apps/libraries that use it are encouraged to keep an existing instance around
 * (using a singleton or similar mechanism).
 * <p/>
 *
 * Background:
 * <ul>
 * <li> The sdk manager has a set of "Package" classes that cover both local
 *      and remote SDK operations.
 * <li> Goal is to split it in 2 cleanly separated part: local sdk parses sdk on disk, and then
 *      there will be a set of "remote package" classes that wrap the downloaded manifest.
 * <li> The local SDK should be a singleton accessible somewhere, so there will be one in ADT
 *      (via the Sdk instance), one in Studio, and one in the command line tool. <br/>
 *      Right now there's a bit of mess with some classes creating a temp LocalSdkParser,
 *      some others using an SdkManager instance, and that needs to be sorted out.
 * <li> As a transition, the SdkManager instance wraps a LocalSdk and use this. Eventually the
 *      SdkManager.java class will go away (its name is totally misleading, for starters.)
 * <li> The current LocalSdkParser stays as-is for compatibility purposes and the goal is also
 *      to totally remove it when the SdkManager class goes away.
 * </ul>
 * @version 2 of the {@code SdkManager} class, essentially.
 */
public class LocalSdk {

    /** Location of the SDK. Maybe null. Can be changed. */
    private File mSdkRoot;
    /** File operation object. (Used for overriding in mock testing.) */
    private final IFileOp mFileOp;
    /** List of package information loaded so far. Lazily populated. */
    private final Multimap<PkgType, LocalPkgInfo> mLocalPackages = TreeMultimap.create();
    /** Directories already parsed into {@link #mLocalPackages}. */
    private final Multimap<PkgType, DirInfo> mVisitedDirs = HashMultimap.create();
    /** A legacy build-tool for older platform-tools < 17. */
    private BuildToolInfo mLegacyBuildTools;

    /**
     * Creates an initial LocalSdk instance with an unknown location.
     */
    public LocalSdk() {
        mFileOp = new FileOp();
    }

    /**
     * Creates an initial LocalSdk instance for a known SDK location.
     *
     * @param sdkRoot The location of the SDK root folder.
     */
    public LocalSdk(@NonNull File sdkRoot) {
        this();
        setLocation(sdkRoot);
    }

    /**
     * Creates an initial LocalSdk instance with an unknown location.
     * This is designed for unit tests to override the {@link FileOp} being used.
     *
     * @param fileOp The alternate {@link FileOp} to use for all file-based interactions.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected LocalSdk(@NonNull IFileOp fileOp) {
        mFileOp = fileOp;
    }

    /*
     * Returns the current IFileOp being used.
     */
    @NonNull
    public IFileOp getFileOp() {
        return mFileOp;
    }

    /**
     * Sets or changes the SDK root location. This also clears any cached information.
     *
     * @param sdkRoot The location of the SDK root folder.
     */
    public void setLocation(@NonNull File sdkRoot) {
        assert sdkRoot != null;
        mSdkRoot = sdkRoot;
        clearLocalPkg(PkgType.PKG_ALL);
    }

    /**
     * Location of the SDK. Maybe null. Can be changed.
     *
     * @return The location of the SDK. Null if not initialized yet.
     */
    @Nullable
    public File getLocation() {
        return mSdkRoot;
    }

    /**
     * Clear the tracked visited folders & the cached {@link LocalPkgInfo} for the
     * given filter types.
     *
     * @param filters An OR of the PkgType.PKG_ constants or {@link PkgType#PKG_ALL} to clear everything.
     */
    public void clearLocalPkg(@NonNull EnumSet<PkgType> filters) {
        mLegacyBuildTools = null;

        for (PkgType filter : filters) {
            mVisitedDirs.removeAll(filter);
            mLocalPackages.removeAll(filter);
        }
    }

    /**
     * Check the tracked visited folders to see if anything has changed for the
     * requested filter types.
     * This does not refresh or reload any package information.
     *
     * @param filters An OR of the PkgType.PKG_ constants or {@link PkgType#PKG_ALL} to clear everything.
     */
    public boolean hasChanged(@NonNull EnumSet<PkgType> filters) {
        for (PkgType filter : filters) {
            for(DirInfo dirInfo : mVisitedDirs.get(filter)) {
                if (dirInfo.hasChanged()) {
                    return true;
                }
            }
        }

        return false;
    }



    //--------- Generic querying ---------

    /**
     * Retrieves information on a package identified by an {@link AndroidVersion}.
     *
     * Note: don't use this for {@link PkgType#PKG_SYS_IMAGES} since there can be more than
     * one ABI and this method only returns a single package per filter type.
     *
     * @param filter {@link PkgType#PKG_PLATFORMS}, {@link PkgType#PKG_SAMPLES}
     *                or {@link PkgType#PKG_SOURCES}.
     * @param version The {@link AndroidVersion} specific for this package type.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull AndroidVersion version) {
        assert filter == PkgType.PKG_PLATFORMS ||
               filter == PkgType.PKG_SAMPLES ||
               filter == PkgType.PKG_SOURCES;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.hasAndroidVersion() && d.getAndroidVersion().equals(version)) {
                return pkg;
            }
        }

        return null;
    }

    /**
     * Retrieves information on a package identified by its {@link FullRevision}.
     * <p/>
     * Note that {@link PkgType#PKG_TOOLS} and {@link PkgType#PKG_PLATFORM_TOOLS}
     * are unique in a local SDK so you'll want to use {@link #getPkgInfo(PkgType)}
     * to retrieve them instead.
     *
     * @param filter {@link PkgType#PKG_BUILD_TOOLS}.
     * @param revision The {@link FullRevision} uniquely identifying this package.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull FullRevision revision) {

        assert filter == PkgType.PKG_BUILD_TOOLS;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            IPkgDesc d = pkg.getDesc();
            if (d.hasFullRevision() && d.getFullRevision().equals(revision)) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Retrieves information on a package identified by its {@link String} vendor/path.
     *
     * @param filter {@link PkgType#PKG_EXTRAS}, {@link PkgType#PKG_ADDONS},
     *               {@link PkgType#PKG_PLATFORMS}.
     * @param vendorPath The vendor/path uniquely identifying this package.
     * @return An existing package information or null if not found.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter, @NonNull String vendorPath) {

        assert filter == PkgType.PKG_EXTRAS ||
               filter == PkgType.PKG_ADDONS ||
               filter == PkgType.PKG_PLATFORMS;

        for (LocalPkgInfo pkg : getPkgsInfos(filter)) {
            if (pkg.getDesc().hasPath() && vendorPath.equals(pkg.getDesc().getPath())) {
               return pkg;
           }
       }
       return null;
    }

    /**
     * Retrieves information on an extra package identified by its {@link String} vendor/path.
     *
     * @param vendorPath The vendor/path uniquely identifying this package.
     * @return An existing extra package information or null if not found.
     */
    @Nullable
    public LocalExtraPkgInfo getExtra(@NonNull String vendorPath) {
        return (LocalExtraPkgInfo) getPkgInfo(PkgType.PKG_EXTRAS, vendorPath);
    }

    /**
     * For unique local packages.
     * Returns the cached LocalPkgInfo for the requested type.
     * Loads it from disk if not cached.
     *
     * @param filter {@link PkgType#PKG_TOOLS} or {@link PkgType#PKG_PLATFORM_TOOLS}
     *               or {@link PkgType#PKG_DOCS}.
     * @return null if the package is not installed.
     */
    @Nullable
    public LocalPkgInfo getPkgInfo(@NonNull PkgType filter) {

        assert filter == PkgType.PKG_TOOLS ||
               filter == PkgType.PKG_PLATFORM_TOOLS ||
               filter == PkgType.PKG_DOCS;

        if (filter != PkgType.PKG_TOOLS &&
            filter != PkgType.PKG_PLATFORM_TOOLS &&
            filter != PkgType.PKG_DOCS) {
            return null;
        }

        Collection<LocalPkgInfo> existing = mLocalPackages.get(filter);
        assert existing.size() <= 1;
        if (existing.size() > 0) {
            return existing.iterator().next();
        }

        File uniqueDir = new File(mSdkRoot, filter.getFolderName());
        LocalPkgInfo info = null;

        if (!mVisitedDirs.containsEntry(filter, uniqueDir)) {
            switch(filter) {
            case PKG_TOOLS:
                info = scanTools(uniqueDir);
                break;
            case PKG_PLATFORM_TOOLS:
                info = scanPlatformTools(uniqueDir);
                break;
            case PKG_DOCS:
                info = scanDoc(uniqueDir);
                break;
            case PKG_BUILD_TOOLS:
            case PKG_EXTRAS:
            case PKG_PLATFORMS:
            case PKG_ADDONS:
            case PKG_SAMPLES:
            case PKG_SOURCES:
            case PKG_SYS_IMAGES:
                break;
            }
        }

        // Whether we have found a valid pkg or not, this directory has been visited.
        mVisitedDirs.put(filter, new DirInfo(uniqueDir));

        if (info != null) {
            mLocalPackages.put(filter, info);
        }

        return info;
    }

    /**
     * Retrieve all the info about the requested package type.
     * This is used for the package types that have one or more instances, each with different
     * versions.
     * <p/>
     * Note: you can use this with {@link PkgType#PKG_TOOLS}, {@link PkgType#PKG_PLATFORM_TOOLS} and
     * {@link PkgType#PKG_DOCS} but since there can only be one package of these types, it is
     * more efficient to use {@link #getPkgInfo(PkgType)} to query them.
     *
     * @param filter One of {@link PkgType} constants.
     * @return A list (possibly empty) of matching installed packages. Never returns null.
     */
    @NonNull
    public LocalPkgInfo[] getPkgsInfos(@NonNull PkgType filter) {
        return getPkgsInfos(EnumSet.of(filter));
    }

    /**
     * Retrieve all the info about the requested package types.
     * This is used for the package types that have one or more instances, each with different
     * versions.
     * <p/>
     * To force the LocalSdk parser to load <b>everything</b>, simply call this method
     * with the {@link PkgType#PKG_ALL} argument to load all the known package types.
     * <p/>
     * Note: you can use this with {@link PkgType#PKG_TOOLS}, {@link PkgType#PKG_PLATFORM_TOOLS} and
     * {@link PkgType#PKG_DOCS} but since there can only be one package of these types, it is
     * more efficient to use {@link #getPkgInfo(PkgType)} to query them.
     *
     * @param filters One or more of {@link PkgType#PKG_ADDONS}, {@link PkgType#PKG_PLATFORMS},
     *                               {@link PkgType#PKG_BUILD_TOOLS}, {@link PkgType#PKG_EXTRAS},
     *                               {@link PkgType#PKG_SOURCES}, {@link PkgType#PKG_SYS_IMAGES}
     * @return A list (possibly empty) of matching installed packages. Never returns null.
     */
    @NonNull
    public LocalPkgInfo[] getPkgsInfos(@NonNull EnumSet<PkgType> filters) {
        List<LocalPkgInfo> list = Lists.newArrayList();

        for (PkgType filter : filters) {
            if (filter == PkgType.PKG_TOOLS ||
                    filter == PkgType.PKG_PLATFORM_TOOLS ||
                    filter == PkgType.PKG_DOCS) {
                LocalPkgInfo info = getPkgInfo(filter);
                if (info != null) {
                    list.add(info);
                }
            } else {
                Collection<LocalPkgInfo> existing = mLocalPackages.get(filter);
                assert existing != null; // Multimap returns an empty set if not found

                if (!existing.isEmpty()) {
                    list.addAll(existing);
                    continue;
                }

                File subDir = new File(mSdkRoot, filter.getFolderName());

                if (!mVisitedDirs.containsEntry(filter, subDir)) {
                    switch(filter) {
                    case PKG_BUILD_TOOLS:
                        scanBuildTools(subDir, existing);
                        break;

                    case PKG_PLATFORMS:
                        scanPlatforms(subDir, existing);
                        break;

                    case PKG_SYS_IMAGES:
                        scanSysImages(subDir, existing);
                        break;

                    case PKG_ADDONS:
                        scanAddons(subDir, existing);
                        break;

                    case PKG_SAMPLES:
                        scanSamples(subDir, existing);
                        break;

                    case PKG_SOURCES:
                        scanSources(subDir, existing);
                        break;

                    case PKG_EXTRAS:
                        scanExtras(subDir, existing);
                        break;

                    case PKG_TOOLS:
                    case PKG_PLATFORM_TOOLS:
                    case PKG_DOCS:
                        break;
                    }
                    mVisitedDirs.put(filter, new DirInfo(subDir));
                    list.addAll(existing);
                }
            }
        }

        return list.toArray(new LocalPkgInfo[list.size()]);
    }

    //---------- Package-specific querying --------

    /**
     * Returns the {@link BuildToolInfo} for the given revision.
     *
     * @param revision The requested revision.
     * @return A {@link BuildToolInfo}. Can be null if {@code revision} is null or is
     *  not part of the known set returned by {@code getPkgsInfos(PkgType.PKG_BUILD_TOOLS)}.
     */
    @Nullable
    public BuildToolInfo getBuildTool(@Nullable FullRevision revision) {
        LocalPkgInfo pkg = getPkgInfo(PkgType.PKG_BUILD_TOOLS, revision);
        if (pkg instanceof LocalBuildToolPkgInfo) {
            return ((LocalBuildToolPkgInfo) pkg).getBuildToolInfo();
        }
        return null;
    }

    /**
     * Returns the highest build-tool revision known, or null if there are are no build-tools.
     * <p/>
     * If no specific build-tool package is installed but the platform-tools is lower than 17,
     * then this creates and returns a "legacy" built-tool package using platform-tools.
     * (We only split build-tools out of platform-tools starting with revision 17,
     *  before they were both the same thing.)
     *
     * @return The highest build-tool revision known, or null.
     */
    @Nullable
    public BuildToolInfo getLatestBuildTool() {
        if (mLegacyBuildTools != null) {
            return mLegacyBuildTools;
        }

        LocalPkgInfo[] pkgs = getPkgsInfos(PkgType.PKG_BUILD_TOOLS);

        if (pkgs.length == 0) {
            LocalPkgInfo ptPkg = getPkgInfo(PkgType.PKG_PLATFORM_TOOLS);
            if (ptPkg instanceof LocalPlatformToolPkgInfo &&
                    ptPkg.getDesc().getFullRevision().compareTo(new FullRevision(17)) < 0) {
                // older SDK, create a compatible build-tools
                mLegacyBuildTools = createLegacyBuildTools((LocalPlatformToolPkgInfo) ptPkg);
                return mLegacyBuildTools;
            }
            return null;
        }

        assert pkgs.length > 0;

        // Note: the pkgs come from a TreeMultimap so they should already be sorted.
        // Just in case, sort them again.
        Arrays.sort(pkgs);

        // LocalBuildToolPkgInfo's comparator sorts on its FullRevision so we just
        // need to take the latest element.
        LocalPkgInfo pkg = pkgs[pkgs.length - 1];
        if (pkg instanceof LocalBuildToolPkgInfo) {
            return ((LocalBuildToolPkgInfo) pkg).getBuildToolInfo();
        }

        return null;
    }

    @NonNull
    private BuildToolInfo createLegacyBuildTools(@NonNull LocalPlatformToolPkgInfo ptInfo) {
        File platformTools = new File(getLocation(), SdkConstants.FD_PLATFORM_TOOLS);
        File platformToolsLib = ptInfo.getLocalDir();
        File platformToolsRs = new File(platformTools, SdkConstants.FN_FRAMEWORK_RENDERSCRIPT);

        return new BuildToolInfo(
                ptInfo.getDesc().getFullRevision(),
                platformTools,
                new File(platformTools, SdkConstants.FN_AAPT),
                new File(platformTools, SdkConstants.FN_AIDL),
                new File(platformTools, SdkConstants.FN_DX),
                new File(platformToolsLib, SdkConstants.FN_DX_JAR),
                new File(platformTools, SdkConstants.FN_RENDERSCRIPT),
                new File(platformToolsRs, SdkConstants.FN_FRAMEWORK_INCLUDE),
                new File(platformToolsRs, SdkConstants.FN_FRAMEWORK_INCLUDE_CLANG),
                null,
                null,
                null,
                null);
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    @Nullable
    public IAndroidTarget getTargetFromHashString(@Nullable String hash) {

        if (hash != null) {
            boolean isPlatform = AndroidTargetHash.isPlatform(hash);
            LocalPkgInfo[] pkgs =
                getPkgsInfos(isPlatform ? PkgType.PKG_PLATFORMS : PkgType.PKG_ADDONS);

            for (LocalPkgInfo pkg : pkgs) {
                if (pkg instanceof LocalPlatformPkgInfo) {
                    IAndroidTarget target = ((LocalPlatformPkgInfo) pkg).getAndroidTarget();
                    if (target != null &&
                            hash.equals(AndroidTargetHash.getTargetHashString(target))) {
                        return target;
                    }
                }
            }
        }
        return null;
    }

    // -------------

    /**
     * Try to find a tools package at the given location.
     * Returns null if not found.
     */
    private LocalToolPkgInfo scanTools(File toolFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(toolFolder, SdkConstants.FN_SOURCE_PROP));
        FullRevision rev = PackageParserUtils.getPropertyFull(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        FullRevision minPlatToolsRev =
            PackageParserUtils.getPropertyFull(props, PkgProps.MIN_PLATFORM_TOOLS_REV);
        if (minPlatToolsRev == null) {
            minPlatToolsRev = FullRevision.NOT_SPECIFIED;
        }

        LocalToolPkgInfo info = new LocalToolPkgInfo(this, toolFolder, props, rev, minPlatToolsRev);

        // We're not going to check that all tools are present. At the very least
        // we should expect to find android and an emulator adapted to the current OS.
        boolean hasEmulator = false;
        boolean hasAndroid = false;
        String android1 = SdkConstants.androidCmdName().replace(".bat", ".exe");
        String android2 = android1.indexOf('.') == -1 ? null : android1.replace(".exe", ".bat");
        File[] files = mFileOp.listFiles(toolFolder);
        for (File file : files) {
            String name = file.getName();
            if (SdkConstants.FN_EMULATOR.equals(name)) {
                hasEmulator = true;
            }
            if (android1.equals(name) || (android2 != null && android2.equals(name))) {
                hasAndroid = true;
            }
        }
        if (!hasAndroid) {
            info.appendLoadError("Missing %1$s", SdkConstants.androidCmdName());
        }
        if (!hasEmulator) {
            info.appendLoadError("Missing %1$s", SdkConstants.FN_EMULATOR);
        }

        return info;
    }

    /**
     * Try to find a platform-tools package at the given location.
     * Returns null if not found.
     */
    private LocalPlatformToolPkgInfo scanPlatformTools(File ptFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(ptFolder, SdkConstants.FN_SOURCE_PROP));
        FullRevision rev = PackageParserUtils.getPropertyFull(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        LocalPlatformToolPkgInfo info = new LocalPlatformToolPkgInfo(this, ptFolder, props, rev);
        return info;
    }

    /**
     * Try to find a docs package at the given location.
     * Returns null if not found.
     */
    private LocalDocPkgInfo scanDoc(File docFolder) {
        // Can we find some properties?
        Properties props = parseProperties(new File(docFolder, SdkConstants.FN_SOURCE_PROP));
        MajorRevision rev = PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            return null;
        }

        try {
            AndroidVersion vers = new AndroidVersion(props);
            LocalDocPkgInfo info = new LocalDocPkgInfo(this, docFolder, props, vers, rev);

            // To start with, a doc folder should have an "index.html" to be acceptable.
            // We don't actually check the content of the file.
            if (!mFileOp.isFile(new File(docFolder, "index.html"))) {
                info.appendLoadError("Missing index.html");
            }
            return info;

        } catch (AndroidVersionException e) {
            return null; // skip invalid or missing android version.
        }
    }

    private void scanBuildTools(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        // The build-tool root folder contains a list of per-revision folders.
        for (File buildToolDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(buildToolDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_BUILD_TOOLS, buildToolDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_BUILD_TOOLS, new DirInfo(buildToolDir));

            Properties props = parseProperties(new File(buildToolDir, SdkConstants.FN_SOURCE_PROP));
            FullRevision rev = PackageParserUtils.getPropertyFull(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            BuildToolInfo btInfo = new BuildToolInfo(rev, buildToolDir);
            LocalBuildToolPkgInfo pkgInfo =
                new LocalBuildToolPkgInfo(this, buildToolDir, props, rev, btInfo);
            outCollection.add(pkgInfo);
        }
    }

    private void scanPlatforms(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(platformDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_PLATFORMS, platformDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_PLATFORMS, new DirInfo(platformDir));

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            MajorRevision rev = PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            FullRevision minToolsRev =
                PackageParserUtils.getPropertyFull(props, PkgProps.MIN_TOOLS_REV);
            if (minToolsRev == null) {
                minToolsRev = FullRevision.NOT_SPECIFIED;
            }

            try {
                AndroidVersion vers = new AndroidVersion(props);

                LocalPlatformPkgInfo pkgInfo =
                    new LocalPlatformPkgInfo(this, platformDir, props, vers, rev, minToolsRev);
                outCollection.add(pkgInfo);

            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanAddons(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File addonDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(addonDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_ADDONS, addonDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_ADDONS, new DirInfo(addonDir));

            Properties props = parseProperties(new File(addonDir, SdkConstants.FN_SOURCE_PROP));
            MajorRevision rev = PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            try {
                AndroidVersion vers = new AndroidVersion(props);

                LocalAddonPkgInfo pkgInfo =
                    new LocalAddonPkgInfo(this, addonDir, props, vers, rev);
                outCollection.add(pkgInfo);

            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanSysImages(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(platformDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_SYS_IMAGES, platformDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_SYS_IMAGES, new DirInfo(platformDir));

            for (File abiDir : mFileOp.listFiles(platformDir)) {
                if (!mFileOp.isDirectory(abiDir) ||
                        mVisitedDirs.containsEntry(PkgType.PKG_SYS_IMAGES, abiDir)) {
                    continue;
                }
                mVisitedDirs.put(PkgType.PKG_SYS_IMAGES, new DirInfo(abiDir));

                Properties props = parseProperties(new File(abiDir, SdkConstants.FN_SOURCE_PROP));
                MajorRevision rev =
                    PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
                if (rev == null) {
                    continue; // skip, no revision
                }

                try {
                    AndroidVersion vers = new AndroidVersion(props);

                    LocalSysImgPkgInfo pkgInfo =
                        new LocalSysImgPkgInfo(this, abiDir, props, vers, abiDir.getName(), rev);
                    outCollection.add(pkgInfo);

                } catch (AndroidVersionException e) {
                    continue; // skip invalid or missing android version.
                }
            }
        }
    }

    private void scanSamples(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(platformDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_SAMPLES, platformDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_SAMPLES, new DirInfo(platformDir));

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            MajorRevision rev = PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            FullRevision minToolsRev =
                PackageParserUtils.getPropertyFull(props, PkgProps.MIN_TOOLS_REV);
            if (minToolsRev == null) {
                minToolsRev = FullRevision.NOT_SPECIFIED;
            }

            try {
                AndroidVersion vers = new AndroidVersion(props);

                LocalSamplePkgInfo pkgInfo =
                    new LocalSamplePkgInfo(this, platformDir, props, vers, rev, minToolsRev);
                outCollection.add(pkgInfo);
            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanSources(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        // The build-tool root folder contains a list of per-revision folders.
        for (File platformDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(platformDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_SOURCES, platformDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_SOURCES, new DirInfo(platformDir));

            Properties props = parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));
            MajorRevision rev = PackageParserUtils.getPropertyMajor(props, PkgProps.PKG_REVISION);
            if (rev == null) {
                continue; // skip, no revision
            }

            try {
                AndroidVersion vers = new AndroidVersion(props);

                LocalSourcePkgInfo pkgInfo =
                    new LocalSourcePkgInfo(this, platformDir, props, vers, rev);
                outCollection.add(pkgInfo);
            } catch (AndroidVersionException e) {
                continue; // skip invalid or missing android version.
            }
        }
    }

    private void scanExtras(File collectionDir, Collection<LocalPkgInfo> outCollection) {
        for (File vendorDir : mFileOp.listFiles(collectionDir)) {
            if (!mFileOp.isDirectory(vendorDir) ||
                    mVisitedDirs.containsEntry(PkgType.PKG_EXTRAS, vendorDir)) {
                continue;
            }
            mVisitedDirs.put(PkgType.PKG_EXTRAS, new DirInfo(vendorDir));

            for (File extraDir : mFileOp.listFiles(vendorDir)) {
                if (!mFileOp.isDirectory(extraDir) ||
                        mVisitedDirs.containsEntry(PkgType.PKG_EXTRAS, extraDir)) {
                    continue;
                }
                mVisitedDirs.put(PkgType.PKG_EXTRAS, new DirInfo(extraDir));

                Properties props = parseProperties(new File(extraDir, SdkConstants.FN_SOURCE_PROP));
                NoPreviewRevision rev =
                    PackageParserUtils.getPropertyNoPreview(props, PkgProps.PKG_REVISION);
                if (rev == null) {
                    continue; // skip, no revision
                }

                LocalExtraPkgInfo pkgInfo = new LocalExtraPkgInfo(this,
                                                                  extraDir,
                                                                  props,
                                                                  vendorDir.getName(),
                                                                  extraDir.getName(),
                                                                  rev);
                outCollection.add(pkgInfo);
            }
        }
    }

    /**
     * Parses the given file as properties file if it exists.
     * Returns null if the file does not exist, cannot be parsed or has no properties.
     */
    private Properties parseProperties(File propsFile) {
        InputStream fis = null;
        try {
            if (mFileOp.exists(propsFile)) {
                fis = mFileOp.newFileInputStream(propsFile);

                Properties props = new Properties();
                props.load(fis);

                // To be valid, there must be at least one property in it.
                if (props.size() > 0) {
                    return props;
                }
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    // -------------

    /**
     * Keeps information on a visited directory to quickly determine if it
     * has changed later. A directory has changed if its timestamp has been
     * modified, or if an underlying source.properties file has changed in
     * timestamp or checksum.
     * <p/>
     * Note that depending on the filesystem & OS, the content of the files in
     * a directory can change without the directory's last-modified property
     * changing. A generic directory monitor would work around that by checking
     * the list of files. Instead here we know that each directory is an SDK
     * directory and the source.property file will change if a new package is
     * installed or updated.
     * <p/>
     * The {@link #hashCode()} and {@link #equals(Object)} methods directly
     * defer to the underlying File object. This allows the DirInfo to be placed
     * into a map and still call {@link Map#containsKey(Object)} with a File
     * object to check whether there's a corresponding DirInfo in the map.
     */
    private class DirInfo {
        @NonNull
        private final File mDir;
        private final long mDirModifiedTS;
        private final long mPropsModifiedTS;
        private final long mPropsChecksum;

        /**
         * Creates a new immutable {@link DirInfo}.
         *
         * @param dir The platform/addon directory of the target. It should be a directory.
         */
        public DirInfo(@NonNull File dir) {
            mDir = dir;
            mDirModifiedTS = mFileOp.lastModified(dir);

            // Capture some info about the source.properties file if it exists.
            // We use propsModifiedTS == 0 to mean there is no props file.
            long propsChecksum = 0;
            long propsModifiedTS = 0;
            File props = new File(dir, SdkConstants.FN_SOURCE_PROP);
            if (mFileOp.isFile(props)) {
                propsModifiedTS = mFileOp.lastModified(props);
                propsChecksum = getFileChecksum(props);
            }
            mPropsModifiedTS = propsModifiedTS;
            mPropsChecksum = propsChecksum;
        }

        /**
         * Checks whether the directory/source.properties attributes have changed.
         *
         * @return True if the directory modified timestamp or
         *  its source.property files have changed.
         */
        public boolean hasChanged() {
            // Does platform directory still exist?
            if (!mFileOp.isDirectory(mDir)) {
                return true;
            }
            // Has platform directory modified-timestamp changed?
            if (mDirModifiedTS != mFileOp.lastModified(mDir)) {
                return true;
            }

            File props = new File(mDir, SdkConstants.FN_SOURCE_PROP);

            // The directory did not have a props file if target was null or
            // if mPropsModifiedTS is 0.
            boolean hadProps = mPropsModifiedTS != 0;

            // Was there a props file and it vanished, or there wasn't and there's one now?
            if (hadProps != mFileOp.isFile(props)) {
                return true;
            }

            if (hadProps) {
                // Has source.props file modified-timestamp changed?
                if (mPropsModifiedTS != mFileOp.lastModified(props)) {
                    return true;
                }
                // Had the content of source.props changed?
                if (mPropsChecksum != getFileChecksum(props)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Computes an adler32 checksum (source.props are small files, so this
         * should be OK with an acceptable collision rate.)
         */
        private long getFileChecksum(@NonNull File file) {
            InputStream fis = null;
            try {
                fis = mFileOp.newFileInputStream(file);
                Adler32 a = new Adler32();
                byte[] buf = new byte[1024];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    a.update(buf, 0, n);
                }
                return a.getValue();
            } catch (Exception ignore) {
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch(Exception ignore) {}
            }
            return 0;
        }

        /** Returns a visual representation of this object for debugging. */
        @Override
        public String toString() {
            String s = String.format("<DirInfo %1$s TS=%2$d", mDir, mDirModifiedTS);  //$NON-NLS-1$
            if (mPropsModifiedTS != 0) {
                s += String.format(" | Props TS=%1$d, Chksum=%2$s",                   //$NON-NLS-1$
                        mPropsModifiedTS, mPropsChecksum);
            }
            return s + ">";                                                           //$NON-NLS-1$
        }

        /**
         * Returns the hashCode of the underlying File object.
         * <p/>
         * When a {@link DirInfo} is placed in a map, what matters is to use the underlying
         * File object as the key so {@link #hashCode()} and {@link #equals(Object)} both
         * return the properties of the underlying File object.
         *
         * @see File#hashCode()
         */
        @Override
        public int hashCode() {
            return mDir.hashCode();
        }

        /**
         * Checks equality of the underlying File object.
         * <p/>
         * When a {@link DirInfo} is placed in a map, what matters is to use the underlying
         * File object as the key so {@link #hashCode()} and {@link #equals(Object)} both
         * return the properties of the underlying File object.
         *
         * @see File#equals(Object)
         */
        @Override
        public boolean equals(Object obj) {
            return mDir.equals(obj);
        };
    }

}
