/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import junit.framework.TestCase;

import java.io.File;

public class VariantConfigurationTest extends TestCase {

    private DefaultProductFlavor mDefaultConfig;
    private DefaultProductFlavor mFlavorConfig;
    private DefaultBuildType mBuildType;

    private static class ManifestParserMock implements ManifestParser {

        private final String mPackageName;

        ManifestParserMock(String packageName) {
            mPackageName = packageName;
        }

        @Nullable
        @Override
        public String getPackage(@NonNull File manifestFile) {
            return mPackageName;
        }

        @Override
        public int getMinSdkVersion(@NonNull File manifestFile) {
            return 0;
        }

        @Override
        public int getTargetSdkVersion(@NonNull File manifestFile) {
            return -1;
        }

        @Nullable
        @Override
        public String getVersionName(@NonNull File manifestFile) {
            return "1.0";
        }

        @Override
        public int getVersionCode(@NonNull File manifestFile) {
            return 1;
        }
    }

    @Override
    protected void setUp() throws Exception {
        mDefaultConfig = new DefaultProductFlavor("main");
        mFlavorConfig = new DefaultProductFlavor("flavor");
        mBuildType = new DefaultBuildType("debug");
    }

    public void testPackageOverrideNone() {
        VariantConfiguration variant = getVariant();

        assertNull(variant.getPackageOverride());
    }

    public void testPackageOverridePackageFromFlavor() {
        mFlavorConfig.setPackageName("foo.bar");

        VariantConfiguration variant = getVariant();

        assertEquals("foo.bar", variant.getPackageOverride());
    }

    public void testPackageOverridePackageFromFlavorWithSuffix() {
        mFlavorConfig.setPackageName("foo.bar");
        mBuildType.setPackageNameSuffix(".fortytwo");

        VariantConfiguration variant = getVariant();

        assertEquals("foo.bar.fortytwo", variant.getPackageOverride());
    }

    public void testPackageOverridePackageFromFlavorWithSuffix2() {
        mFlavorConfig.setPackageName("foo.bar");
        mBuildType.setPackageNameSuffix("fortytwo");

        VariantConfiguration variant = getVariant();

        assertEquals("foo.bar.fortytwo", variant.getPackageOverride());
    }

    public void testPackageOverridePackageWithSuffixOnly() {

        mBuildType.setPackageNameSuffix("fortytwo");

        VariantConfiguration variant = getVariantWithManifestPackage("fake.package.name");

        assertEquals("fake.package.name.fortytwo", variant.getPackageOverride());
    }

    public void testVersionNameFromFlavorWithSuffix() {
        mFlavorConfig.setVersionName("1.0");
        mBuildType.setVersionNameSuffix("-DEBUG");

        VariantConfiguration variant = getVariant();

        assertEquals("1.0-DEBUG", variant.getVersionName());
    }

    public void testVersionNameWithSuffixOnly() {
        mBuildType.setVersionNameSuffix("-DEBUG");

        VariantConfiguration variant = getVariantWithManifestVersion("2.0b1");

        assertEquals("2.0b1-DEBUG", variant.getVersionName());
    }

    private VariantConfiguration getVariant() {
        VariantConfiguration variant = new VariantConfiguration(
                mDefaultConfig, new MockSourceProvider("main"),
                mBuildType, new MockSourceProvider("debug"),
                VariantConfiguration.Type.DEFAULT) {
            // don't do validation.
            @Override
            protected void validate() {

            }
        };

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");

        return variant;
    }

    private VariantConfiguration getVariantWithManifestPackage(final String packageName) {
        VariantConfiguration variant = new VariantConfiguration(
                mDefaultConfig, new MockSourceProvider("main"),
                mBuildType, new MockSourceProvider("debug"),
                VariantConfiguration.Type.DEFAULT) {
            @Override
            public String getPackageFromManifest() {
                return packageName;
            }
            // don't do validation.
            @Override
            protected void validate() {

            }
        };

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");
        return variant;
    }

    private VariantConfiguration getVariantWithManifestVersion(final String versionName) {
        VariantConfiguration variant = new VariantConfiguration(
                mDefaultConfig, new MockSourceProvider("main"),
                mBuildType, new MockSourceProvider("debug"),
                VariantConfiguration.Type.DEFAULT) {
            @Override
            public String getVersionNameFromManifest() {
                return versionName;
            }
            // don't do validation.
            @Override
            protected void validate() {

            }
        };

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");
        return variant;
    }
}
