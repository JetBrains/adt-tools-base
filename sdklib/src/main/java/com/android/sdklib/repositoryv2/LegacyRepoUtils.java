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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.FallbackLocalRepoLoader;
import com.android.repository.api.FallbackRemoteRepoLoader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager.LayoutlibVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repositoryv2.meta.AddonFactory;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.meta.RepoFactory;
import com.android.sdklib.repositoryv2.meta.SdkCommonFactory;
import com.android.sdklib.repositoryv2.meta.SysImgFactory;

/**
 * Utilities used by the {@link FallbackLocalRepoLoader} and {@link FallbackRemoteRepoLoader}s to
 * convert {@link IPkgDesc}s into forms useful to {@link RepoManager}.
 */
public class LegacyRepoUtils {

    /**
     * Convert a {@link IPkgDesc} and other old-style information into a {@link TypeDetails}.
     */
    public static TypeDetails createTypeDetails(@NonNull IPkgDesc desc,
            @Nullable LayoutlibVersion layoutLibVersion,
            RepoManager manager, ProgressIndicator progress) {

        CommonFactory commonFactory = (CommonFactory) manager.getCommonModule()
                .createLatestFactory();
        SdkCommonFactory sdkFactory = (SdkCommonFactory) AndroidSdkHandler.getInstance()
                .getCommonModule(progress).createLatestFactory();
        SchemaModule repoExt = AndroidSdkHandler.getInstance().getRepositoryModule(progress);
        SchemaModule addonExt = AndroidSdkHandler.getInstance().getAddonModule(progress);
        SchemaModule sysImgExt = AndroidSdkHandler.getInstance().getSysImgModule(progress);
        RepoFactory repoFactory = (RepoFactory) repoExt.createLatestFactory();
        AddonFactory addonFactory = (AddonFactory) addonExt.createLatestFactory();
        SysImgFactory sysImgFactory = (SysImgFactory) sysImgExt.createLatestFactory();

        AndroidVersion androidVersion = desc.getAndroidVersion();

        if (desc.getType() == PkgType.PKG_TOOLS) {
            return (TypeDetails) repoFactory.createToolDetailsType();
        } else if (desc.getType() == PkgType.PKG_PLATFORM_TOOLS) {
            return (TypeDetails) repoFactory.createPlatformToolDetailsType();
        } else if (desc.getType() == PkgType.PKG_BUILD_TOOLS) {
            return (TypeDetails) repoFactory.createBuildToolDetailsType();
        } else if (desc.getType() == PkgType.PKG_DOC) {
            return (TypeDetails) repoFactory.createDocDetailsType();
        } else if (desc.getType() == PkgType.PKG_PLATFORM) {
            DetailsTypes.PlatformDetailsType details = repoFactory.createPlatformDetailsType();

            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            details.setCodename(androidVersion.getCodename());
            if (layoutLibVersion != null) {
                DetailsTypes.PlatformDetailsType.LayoutlibType layoutLib = repoFactory
                        .createLayoutlibType();
                layoutLib.setApi(layoutLibVersion.getApi());
                layoutLib.setRevision(commonFactory
                        .createRevisionType(new Revision(layoutLibVersion.getRevision())));
                details.setLayoutlib(layoutLib);
            }
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_SYS_IMAGE ||
                desc.getType() == PkgType.PKG_ADDON_SYS_IMAGE) {
            DetailsTypes.SysImgDetailsType details = sysImgFactory.createSysImgDetailsType();
            details.setAbi(desc.getPath());
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            com.android.sdklib.repository.descriptors.IdDisplay tagIdDisplay = desc.getTag();
            if (tagIdDisplay != null) {
                IdDisplay tag = sdkFactory.createIdDisplayType();
                tag.setId(tagIdDisplay.getId());
                tag.setDisplay(tagIdDisplay.getDisplay());
                details.setTag(tag);
            }
            com.android.sdklib.repository.descriptors.IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_ADDON) {
            DetailsTypes.AddonDetailsType details = addonFactory.createAddonDetailsType();
            com.android.sdklib.repository.descriptors.IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_SAMPLE) {
            // Obsolete, ignore
            return null;
        } else if (desc.getType() == PkgType.PKG_SOURCE) {
            DetailsTypes.SourceDetailsType details = repoFactory.createSourceDetailsType();
            assert androidVersion != null;
            details.setApiLevel(androidVersion.getApiLevel());
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_EXTRA) {
            DetailsTypes.ExtraDetailsType details = addonFactory.createExtraDetailsType();
            com.android.sdklib.repository.descriptors.IdDisplay vendorIdDisplay = desc.getVendor();
            if (vendorIdDisplay != null) {
                IdDisplay vendor = sdkFactory.createIdDisplayType();
                vendor.setId(vendorIdDisplay.getId());
                vendor.setDisplay(vendorIdDisplay.getDisplay());
                details.setVendor(vendor);
            }
            return (TypeDetails) details;
        } else if (desc.getType() == PkgType.PKG_NDK) {
            return (TypeDetails) repoFactory.createNdkDetailsType();
        } else {
            return null;
        }
    }

    /**
     * Gets the {@link RepoPackage#getDisplayName()} return value from an {@link IPkgDesc}.
     */
    public static String getDisplayName(IPkgDesc legacy) {
        String result = legacy.getListDisplay();
        if (result != null) {
            return result;
        }
        if (legacy.getType() == PkgType.PKG_PLATFORM) {
            AndroidVersion androidVersion = legacy.getAndroidVersion();
            assert androidVersion != null;
            return SdkVersionInfo.getAndroidName(androidVersion.getFeatureLevel());
        }
        result = legacy.getListDescription();
        if (!result.isEmpty()) {
            return result;
        }
        result = legacy.getName() != null ? legacy.getName().getDisplay() : "";
        if (!result.isEmpty()) {
            return result;
        }
        return legacy.getInstallId();
    }
}
