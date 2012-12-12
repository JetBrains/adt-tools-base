/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdklib.internal.repository.packages;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SystemImage;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.repository.PkgProps;

import java.util.Map;
import java.util.Properties;

/**
 * A mock {@link AddonPackage} for testing.
 *
 * By design, this package contains one and only one archive.
 */
public class MockAddonPackage extends AddonPackage {

    /**
     * Creates a {@link MockAddonTarget} with the requested base platform and addon revision
     * and then a {@link MockAddonPackage} wrapping it and a default name of "addon".
     *
     * By design, this package contains one and only one archive.
     */
    public MockAddonPackage(MockPlatformPackage basePlatform, int revision) {
        this("addon", basePlatform, revision); //$NON-NLS-1$
    }

    /**
     * Creates a {@link MockAddonTarget} with the requested base platform and addon revision
     * and then a {@link MockAddonPackage} wrapping it.
     *
     * By design, this package contains one and only one archive.
     */
    public MockAddonPackage(String name, MockPlatformPackage basePlatform, int revision) {
        super(new MockAddonTarget(name, basePlatform.getTarget(), revision), null /*props*/);
    }

    public MockAddonPackage(
            SdkSource source,
            String name,
            MockPlatformPackage basePlatform,
            int revision) {
        super(source,
              new MockAddonTarget(name, basePlatform.getTarget(), revision),
              createProperties(name, basePlatform.getTarget()));
    }

    private static Properties createProperties(String name, IAndroidTarget baseTarget) {
        String vendor = baseTarget.getVendor();
        Properties props = new Properties();
        props.setProperty(PkgProps.ADDON_NAME_ID, name);
        props.setProperty(PkgProps.ADDON_NAME_DISPLAY,
                String.format("The %1$s from %2$s",                  //$NON-NLS-1$
                        name, vendor));
        props.setProperty(PkgProps.ADDON_VENDOR_ID,
                String.format("vendor-id-%1$s", vendor));                   //$NON-NLS-1$
        props.setProperty(PkgProps.ADDON_VENDOR_DISPLAY,
                String.format("The %1$s", vendor));                  //$NON-NLS-1$
        return props;
    }

    /**
     * A mock AddonTarget.
     * This reimplements the minimum needed from the interface for our limited testing needs.
     */
    static class MockAddonTarget implements IAndroidTarget {

        private final IAndroidTarget mParentTarget;
        private final int mRevision;
        private final String mName;
        private ISystemImage[] mSystemImages;

        public MockAddonTarget(String name, IAndroidTarget parentTarget, int revision) {
            mName = name;
            mParentTarget = parentTarget;
            mRevision = revision;
        }

        @Override
        public String getClasspathName() {
            return getName();
        }

        @Override
        public String getShortClasspathName() {
            return getName();
        }

        @Override
        public String getDefaultSkin() {
            return null;
        }

        @Override
        public String getDescription() {
            return getName();
        }

        @Override
        public String getFullName() {
            return getName();
        }

        @Override
        public ISystemImage[] getSystemImages() {
            if (mSystemImages == null) {
                SystemImage si = new SystemImage(
                        FileOp.append(getLocation(), SdkConstants.OS_IMAGES_FOLDER),
                        LocationType.IN_PLATFORM_LEGACY,
                        SdkConstants.ABI_ARMEABI);
                mSystemImages = new SystemImage[] { si };
            }
            return mSystemImages;
        }

        @Override
        public ISystemImage getSystemImage(String abiType) {
            if (SdkConstants.ABI_ARMEABI.equals(abiType)) {
                return getSystemImages()[0];
            }
            return null;
        }

        @Override
        public String getLocation() {
            return "/sdk/add-ons/addon-" + mName;
        }

        @Override
        public IOptionalLibrary[] getOptionalLibraries() {
            return null;
        }

        @Override
        public IAndroidTarget getParent() {
            return mParentTarget;
        }

        @Override
        public String getPath(int pathId) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        @Override
        public String[] getPlatformLibraries() {
            return null;
        }

        @Override
        public String getProperty(String name) {
            return null;
        }

        @Override
        public Integer getProperty(String name, Integer defaultValue) {
            return defaultValue;
        }

        @Override
        public Boolean getProperty(String name, Boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public Map<String, String> getProperties() {
            return null;
        }

        @Override
        public int getRevision() {
            return mRevision;
        }

        @Override
        public String[] getSkins() {
            return null;
        }

        @Override
        public int getUsbVendorId() {
            return 0;
        }

        @Override
        public AndroidVersion getVersion() {
            return mParentTarget.getVersion();
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public String getVendor() {
            return mParentTarget.getVendor();
        }

        @Override
        public String getVersionName() {
            return String.format("mock-addon-%1$d", getVersion().getApiLevel());
        }

        @Override
        public String hashString() {
            return getVersionName();
        }

        /** Returns false for an addon. */
        @Override
        public boolean isPlatform() {
            return false;
        }

        @Override
        public boolean canRunOn(IAndroidTarget target) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        @Override
        public int compareTo(IAndroidTarget o) {
            throw new UnsupportedOperationException("Implement this as needed for tests");
        }

        @Override
        public boolean hasRenderingLibrary() {
            return false;
        }
    }
}
