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

import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.archives.Archive;
import com.android.sdklib.internal.repository.archives.Archive.Arch;
import com.android.sdklib.internal.repository.archives.Archive.Os;
import com.android.sdklib.internal.repository.packages.BrokenPackage;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.internal.repository.sources.SdkRepoSource;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.repository.PkgProps;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import junit.framework.TestCase;

public class PackageTest extends TestCase {

    protected static final String LOCAL_ARCHIVE_PATH = "/local/archive/path";

    /** Local class used to test the abstract Package class */
    protected static class MockPackage extends MajorRevisionPackage {
        public MockPackage(
                SdkSource source,
                Properties props,
                int revision,
                String license,
                String description,
                String descUrl,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
            super(source,
                    props,
                    revision,
                    license,
                    description,
                    descUrl,
                    archiveOs,
                    archiveArch,
                    archiveOsPath);
        }

        @Override
        public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public String getListDescription() {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public String getShortDescription() {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public boolean sameItemAs(Package pkg) {
            throw new UnsupportedOperationException("abstract method not used in test"); //$NON-NLS-1$
        }

        @Override
        public String installId() {
            return "mock-pkg";  //$NON-NLS-1$
        }
    }

    public void testCreate() throws Exception {
        Properties props = createProps();

        Package p = new MockPackage(
                null, //source
                props,
                -1, //revision
                null, //license
                null, //description
                null, //descUrl
                Os.ANY, //archiveOs
                Arch.ANY, //archiveArch
                LOCAL_ARCHIVE_PATH //archiveOsPath
                );

        testCreatedPackage(p);
    }

    public void testSaveProperties() throws Exception {
        Properties props = createProps();

        Package p = new MockPackage(
                null, //source
                props,
                -1, //revision
                null, //license
                null, //description
                null, //descUrl
                Os.ANY, //archiveOs
                Arch.ANY, //archiveArch
                LOCAL_ARCHIVE_PATH //archiveOsPath
                );

        Properties props2 = new Properties();
        p.saveProperties(props2);

        assertEquals(props2, props);
    }

    /**
     * Sets the properties used by {@link #testCreate()} and
     * {@link #testSaveProperties()}.
     * This is protected and used by derived classes to perform
     * a similar creation test.
     */
    protected Properties createProps() {
        Properties props = new Properties();

        // Package properties
        props.setProperty(PkgProps.PKG_REVISION, "42");
        props.setProperty(PkgProps.PKG_LICENSE, "The License");
        props.setProperty(PkgProps.PKG_DESC, "Some description.");
        props.setProperty(PkgProps.PKG_DESC_URL, "http://description/url");
        props.setProperty(PkgProps.PKG_RELEASE_NOTE, "Release Note");
        props.setProperty(PkgProps.PKG_RELEASE_URL, "http://release/note");
        props.setProperty(PkgProps.PKG_SOURCE_URL, "http://source/url");
        props.setProperty(PkgProps.PKG_OBSOLETE, "true");
        return props;
    }

    /**
     * Tests the values set via {@link #createProps()} after the
     * package has been created in {@link #testCreate()}.
     * This is protected and used by derived classes to perform
     * a similar creation test.
     */
    protected void testCreatedPackage(Package p) {
        // Package properties
        assertEquals("42", p.getRevision().toShortString());
        assertEquals("The License", p.getLicense());
        assertEquals("Some description.", p.getDescription());
        assertEquals("http://description/url", p.getDescUrl());
        assertEquals("Release Note", p.getReleaseNote());
        assertEquals("http://release/note", p.getReleaseNoteUrl());
        assertEquals(new SdkRepoSource("http://source/url", null /*uiName*/), p.getParentSource());
        assertTrue(p.isObsolete());

        assertNotNull(p.getArchives());
        assertEquals(1, p.getArchives().length);
        Archive a = p.getArchives()[0];
        assertNotNull(a);
        assertEquals(Os.ANY, a.getOs());
        assertEquals(Arch.ANY, a.getArch());
        assertEquals(LOCAL_ARCHIVE_PATH, a.getLocalOsPath());
    }

    // ----

    public void testCompareTo() throws Exception {
        ArrayList<Package> list = new ArrayList<Package>();
        MockPlatformPackage p1;

        list.add(p1 = new MockPlatformPackage(1, 2));
        list.add(new MockAddonPackage(p1, 3));
        list.add(new MockSystemImagePackage(p1, 4, "x86"));
        list.add(new MockBrokenPackage(BrokenPackage.MIN_API_LEVEL_NOT_SPECIFIED, 1));
        list.add(new MockExtraPackage("vendor", "path", 5, 6));
        list.add(new MockToolPackage(7, 8));

        Collections.sort(list);

        assertEquals(
                "[Android SDK Tools, revision 7, " +
                 "SDK Platform Android android-1, API 1, revision 2, " +
                 "Intel x86 Atom System Image, Android API 1, revision 4, " +
                 "addon, Android API 1, revision 3, " +
                 "Broken package for API 1, " +
                 "Vendor Path, revision 5]",
                Arrays.toString(list.toArray()));
    }

    public void testGetPropertyInt() {
        Properties p = new Properties();

        assertEquals(42, Package.getPropertyInt(p, "key", 42));

        p.setProperty("key", "");
        assertEquals(43, Package.getPropertyInt(p, "key", 43));

        p.setProperty("key", "I am not a number");
        assertEquals(44, Package.getPropertyInt(p, "key", 44));

        p.setProperty("key", "6");
        assertEquals(6, Package.getPropertyInt(p, "key", 45));
    }

}
