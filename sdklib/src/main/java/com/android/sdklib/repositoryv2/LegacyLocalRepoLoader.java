/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.sdklib.repositoryv2;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkManager;
import com.android.sdklib.SystemImage;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalAddonSysImgPkgInfo;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalPlatformPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.local.LocalSysImgPkgInfo;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A {@link FallbackLocalRepoLoader} that uses a {@link LocalSdk} to parse {@link LocalPkgInfo} and
 * convert them into {@link LocalPackage}s.
 */
public class LegacyLocalRepoLoader implements FallbackLocalRepoLoader {

    /**
     * The old {@link LocalSdk} we'll use to parse the old packages.
     */
    private final LocalSdk mLocalSdk;

    /**
     * The {@link RepoManager} we're parsing for.
     */
    private final RepoManager mManager;

    /**
     * Cache of packages found by {@link #mLocalSdk}.
     */
    private Map<File, LocalPkgInfo> mPkgs = null;

    private final FileOp mFop;

    /**
     * Create a new LegacyLocalRepoLoader, based on {@link LocalSdk}.
     *
     * @param root    The root directory of the SDK.
     * @param fop     {@link FileOp} to use. For normal operation should be {@link
     *                FileOpUtils#create()}.
     * @param manager The {@link RepoManager} we're parsing for.
     */
    public LegacyLocalRepoLoader(@NonNull File root, @NonNull FileOp fop,
            @NonNull RepoManager manager) {
        mLocalSdk = new LocalSdk(fop);
        mLocalSdk.setLocation(root);
        mFop = fop;
        mManager = manager;
    }

    /**
     * Tries to parse a package rooted in the specified directory.
     * @return A {@link LocalPackage} if one was found, otherwise null.
     */
    @Override
    @Nullable
    public LocalPackage parseLegacyLocalPackage(@NonNull File dir,
            @NonNull ProgressIndicator progress) {
        if (!mFop.exists(new File(dir, SdkConstants.FN_SOURCE_PROP))) {
            return null;
        }
        Logger.getLogger(getClass().getName())
                .info(String.format("Parsing legacy package: %s", dir));
        LocalPkgInfo info;
        if (mPkgs == null) {
            Map<File, LocalPkgInfo> result = Maps.newHashMap();
            mLocalSdk.clearLocalPkg(PkgType.PKG_ALL);
            for (LocalPkgInfo local : mLocalSdk.getPkgsInfos(PkgType.PKG_ALL)) {
                result.put(local.getLocalDir(), local);
            }
            mPkgs = result;
        }

        info = mPkgs.get(dir);
        if (info == null || info.getDesc().getType().equals(PkgType.PKG_SAMPLE)) {
            // If we had a source.properties in a nonstandard place, or we have a sample package,
            // don't include it.
            return null;
        }

        return new LegacyLocalPackage(info, progress);
    }

    @Override
    public void refresh() {
        mPkgs = null;
    }

    /**
     * {@link LocalPackage} wrapper around a {@link LocalPkgInfo}.
     */
    class LegacyLocalPackage implements LocalPackage {

        private final ProgressIndicator mProgress;

        private final LocalPkgInfo mWrapped;

        LegacyLocalPackage(@NonNull LocalPkgInfo wrapped, @NonNull ProgressIndicator progress) {
            mWrapped = wrapped;
            mProgress = progress;
        }

        @Override
        @NonNull
        public TypeDetails getTypeDetails() {
            int layoutVersion = 0;
            if (mWrapped instanceof LocalPlatformPkgInfo) {
                IAndroidTarget target = ((LocalPlatformPkgInfo) mWrapped).getAndroidTarget();
                if (target instanceof PlatformTarget) {
                    SdkManager.LayoutlibVersion layoutlibVersion = ((PlatformTarget)target).getLayoutlibVersion();
                    layoutVersion = layoutlibVersion == null ? 1 : layoutlibVersion.getApi();
                }
                else if (target instanceof com.android.sdklib.repositoryv2.targets.PlatformTarget) {
                    layoutVersion
                            = ((com.android.sdklib.repositoryv2.targets.PlatformTarget) target)
                            .getLayoutlibApi();
                }
            }
            return LegacyRepoUtils
                    .createTypeDetails(mWrapped.getDesc(), layoutVersion, mProgress);
        }

        @NonNull
        @Override
        public Revision getVersion() {
            return mWrapped.getDesc().getRevision();
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return LegacyRepoUtils.getDisplayName(mWrapped.getDesc());
        }

        @Override
        @Nullable
        public License getLicense() {
            License res = mWrapped.getDesc().getLicense();
            CommonFactory factory = (CommonFactory) mManager.getCommonModule()
                    .createLatestFactory();
            if (res == null) {
                res = factory.createLicenseType();
                res.setValue(mWrapped.getSourceProperties().getProperty(PkgProps.PKG_LICENSE));
                res.setId(String.format("license-%X", mWrapped.getSourceProperties().hashCode()));
                res.setType("text");
            }
            return res;
        }

        @Override
        @NonNull
        public Collection<Dependency> getAllDependencies() {
            List<Dependency> result = Lists.newArrayList();
            Revision rev = mWrapped.getDesc().getMinPlatformToolsRev();
            CommonFactory factory = (CommonFactory) mManager.getCommonModule()
                    .createLatestFactory();
            if (rev != null) {
                result.add(factory.createDependencyType(rev, SdkConstants.FD_PLATFORM_TOOLS));
            }
            rev = mWrapped.getDesc().getMinToolsRev();
            if (rev != null) {
                result.add(factory.createDependencyType(rev, SdkConstants.FD_TOOLS));
            }
            return result;
        }

        @Override
        @NonNull
        public String getPath() {
            switch (mWrapped.getDesc().getType()) {
                case PKG_TOOLS:
                    return SdkConstants.FD_TOOLS;
                case PKG_PLATFORM_TOOLS:
                    return SdkConstants.FD_PLATFORM_TOOLS;
                case PKG_BUILD_TOOLS:
                    return DetailsTypes.getBuildToolsPath(getVersion());
                case PKG_DOC:
                    return SdkConstants.FD_DOCS;
                case PKG_PLATFORM:
                    return DetailsTypes.getPlatformPath(mWrapped.getDesc().getAndroidVersion());
                case PKG_SYS_IMAGE:
                case PKG_ADDON_SYS_IMAGE:
                    ISystemImage sysImg;
                    if (mWrapped instanceof LocalSysImgPkgInfo) {
                        sysImg = ((LocalSysImgPkgInfo) mWrapped).getSystemImage();
                    } else {
                        sysImg = ((LocalAddonSysImgPkgInfo) mWrapped).getSystemImage();
                    }
                    return DetailsTypes.getSysImgPath(
                            sysImg.getAddonVendor(),
                            mWrapped.getDesc().getAndroidVersion(), sysImg.getTag(),
                            mWrapped.getDesc().getPath());
                case PKG_SOURCE:
                    return DetailsTypes.getSourcesPath(mWrapped.getDesc().getAndroidVersion());
                case PKG_EXTRA:
                    return getPathFromLocation();
                case PKG_NDK:
                    return SdkConstants.FD_NDK;
                case PKG_LLDB:
                    return SdkConstants.FD_LLDB;
                default:
                    // This should never happen.
                    return "unknown";
            }
        }

        private String getPathFromLocation() {
            File location = mLocalSdk.getLocation();
            assert location != null;
            return mWrapped.getLocalDir().getAbsolutePath()
                    .substring(location.getAbsolutePath().length() + 1)
                    .replace(File.separatorChar, RepoPackage.PATH_SEPARATOR);
        }

        @Override
        public boolean obsolete() {
            return mWrapped.getDesc().isObsolete();
        }

        @Override
        @NonNull
        public CommonFactory createFactory() {
            return (CommonFactory) mManager.getCommonModule().createLatestFactory();
        }

        @Override
        public int compareTo(@NonNull RepoPackage o) {
            int result = ComparisonChain.start()
              .compare(getPath(), o.getPath())
              .compare(getVersion(), o.getVersion())
              .result();
            if (result != 0) {
                return result;
            }
            if (!(o instanceof LocalPackage)) {
                return getClass().getName().compareTo(o.getClass().getName());
            }
            return 0;
        }

        @Override
        @NonNull
        public File getLocation() {
            return mWrapped.getLocalDir();
        }

        @Override
        public void setInstalledPath(File root) {
            // Ignore, we already know our whole path.
        }
    }
}
