/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.internal.repository.archives.Archive;
import com.android.sdklib.internal.repository.archives.Archive.Arch;
import com.android.sdklib.internal.repository.archives.Archive.Os;
import com.android.sdklib.internal.repository.packages.SystemImagePackage;
import com.android.sdklib.repository.PkgProps;

import java.util.Properties;

public class SystemImagePackageTest extends PackageTest {

    /**
     * SystemImagePackage implicitly generates a local archive wrapper
     * that matches the current platform OS and architecture. Since this
     * is not convenient for testing, this class overrides it to always
     * create archives for any OS and any architecture.
     */
    private static class SysImgPackageFakeArchive extends SystemImagePackage {
        protected SysImgPackageFakeArchive(
                AndroidVersion platformVersion,
                int revision,
                String abi,
                Properties props) {
            super(platformVersion,
                    revision,
                    abi,
                    props,
                    String.format("/sdk/system-images/android-%s/%s",
                            platformVersion.getApiString(), abi));
        }

        @Override
        protected Archive[] initializeArchives(
                Properties props,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
            assert archiveOs == Os.getCurrentOs();
            assert archiveArch == Arch.getCurrentArch();
            return super.initializeArchives(props, Os.ANY, Arch.ANY, LOCAL_ARCHIVE_PATH);
        }
    }

    private SystemImagePackage createSystemImagePackage(Properties props)
            throws AndroidVersionException {
        SystemImagePackage p = new SysImgPackageFakeArchive(
                new AndroidVersion(props),
                1 /*revision*/,
                null /*abi*/,
                props);
        return p;
    }

    @Override
    protected Properties createProps() {
        Properties props = super.createProps();

        // SystemImagePackage properties
        props.setProperty(PkgProps.VERSION_API_LEVEL, "5");
        props.setProperty(PkgProps.SYS_IMG_ABI, "armeabi-v7a");

        return props;
    }

    protected void testCreatedSystemImagePackage(SystemImagePackage p) {
        super.testCreatedPackage(p);

        // SystemImagePackage properties
        assertEquals("API 5", p.getAndroidVersion().toString());
        assertEquals("armeabi-v7a", p.getAbi());
    }

    // ----

    @Override
    public final void testCreate() throws Exception {
        Properties props = createProps();
        SystemImagePackage p = createSystemImagePackage(props);

        testCreatedSystemImagePackage(p);
    }

    @Override
    public void testSaveProperties() throws Exception {
        Properties props = createProps();
        SystemImagePackage p = createSystemImagePackage(props);

        Properties props2 = new Properties();
        p.saveProperties(props2);

        assertEquals(props2, props);
    }

    public void testSameItemAs() throws Exception {
        Properties props1 = createProps();
        SystemImagePackage p1 = createSystemImagePackage(props1);
        assertTrue(p1.sameItemAs(p1));

        // different abi, same version
        Properties props2 = new Properties(props1);
        props2.setProperty(PkgProps.SYS_IMG_ABI, "x86");
        SystemImagePackage p2 = createSystemImagePackage(props2);
        assertFalse(p1.sameItemAs(p2));
        assertFalse(p2.sameItemAs(p1));

        // different abi, different version
        props2.setProperty(PkgProps.VERSION_API_LEVEL, "6");
        p2 = createSystemImagePackage(props2);
        assertFalse(p1.sameItemAs(p2));
        assertFalse(p2.sameItemAs(p1));

        // same abi, different version
        Properties props3 = new Properties(props1);
        props3.setProperty(PkgProps.VERSION_API_LEVEL, "6");
        SystemImagePackage p3 = createSystemImagePackage(props3);
        assertFalse(p1.sameItemAs(p3));
        assertFalse(p3.sameItemAs(p1));
    }

    public void testInstallId() throws Exception {
        Properties props = createProps();
        SystemImagePackage p = createSystemImagePackage(props);

        assertEquals("sysimg-5", p.installId());
    }
}
