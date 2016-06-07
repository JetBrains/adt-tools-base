/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.sdklib.repository;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import java.util.Comparator;
import java.util.Map;

/**
 * Tests for {@link AndroidSdkHandler}
 */
public class AndroidSdkHandlerTest extends TestCase {

    public void testGetLatestPackage() {
        Map<String, LocalPackage> locals = Maps.newHashMap();

        FakePackage p1_1 = new FakePackage("p;1.1", Revision.parseRevision("1.1"), null);
        locals.put("p;1.1", p1_1);
        FakePackage p1_20 = new FakePackage("p;1.20", Revision.parseRevision("1.20"), null);
        locals.put("p;1.20", p1_20);
        FakePackage p2_1 = new FakePackage("p;2.1", Revision.parseRevision("2.1"), null);
        locals.put("p;2.1", p2_1);
        FakePackage p2_2_rc3 = new FakePackage("p;2.2-rc3", Revision.parseRevision("2.2-rc3"),
                null);
        locals.put("p;2.2-rc3", p2_2_rc3);

        FakePackage qr2_0 = new FakePackage("q;r;2.0", Revision.parseRevision("2.0"), null);
        locals.put("q;r;2.0", qr2_0);
        FakePackage qr2_1 = new FakePackage("q;r;2.1", Revision.parseRevision("2.1"), null);
        locals.put("q;r;2.1", qr2_1);

        RepositoryPackages packages = new RepositoryPackages();
        packages.setLocalPkgInfos(locals);

        LocalPackage latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                false, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals(latest.getPath(), "p;2.1");

        LocalPackage earliest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                false, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>reverseOrder());
        assertNotNull(earliest);
        assertEquals(earliest.getPath(), "p;1.1");

        LocalPackage longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                false, // allowPreview
                String::length,
                Comparator.<Integer>naturalOrder());
        assertNotNull(longest);
        assertEquals(longest.getPath(), "p;1.20");

        longest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                true, // allowPreview
                String::length,
                Comparator.<Integer>naturalOrder());
        assertNotNull(longest);
        assertEquals(longest.getPath(), "p;2.2-rc3");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("p"),
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals(latest.getPath(), "p;2.2-rc3");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("q;r"),
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNotNull(latest);
        assertEquals(latest.getPath(), "q;r;2.1");

        latest = AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                packages.getLocalPackagesForPrefix("o"),
                true, // allowPreview
                Revision::parseRevision,
                Comparator.<Revision>naturalOrder());
        assertNull(latest);
    }

    public void testGetLatestPackageException() {
        Map<String, LocalPackage> locals = Maps.newHashMap();

        FakePackage p1_1 = new FakePackage("p;1.1", Revision.parseRevision("1.1"), null);
        locals.put("p;1.1", p1_1);
        FakePackage pgarbage = new FakePackage("p;garbage", Revision.parseRevision("1.2.3"), null);
        locals.put("p;garbage", pgarbage);

        RepositoryPackages packages = new RepositoryPackages();

        packages.setLocalPkgInfos(locals);
        try {
            AndroidSdkHandler.getLatestPackageFromPrefixCollection(
                    packages.getLocalPackagesForPrefix("p"),
                    false, // allowPreview
                    Revision::parseRevision,
                    Comparator.<Revision>naturalOrder());
            fail();
        } catch (NumberFormatException ignored) {
        }
    }
}
