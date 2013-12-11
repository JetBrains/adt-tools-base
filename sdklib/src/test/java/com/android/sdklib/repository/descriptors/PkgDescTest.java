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

package com.android.sdklib.repository.descriptors;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;

import junit.framework.TestCase;

public class PkgDescTest extends TestCase {

    public final void testPkgDescTool() {
        IPkgDesc p = PkgDesc.newTool(new FullRevision(1, 2, 3, 4), new FullRevision(5, 6, 7, 8));

        assertEquals(PkgType.PKG_TOOLS, p.getType());

        assertTrue(p.hasFullRevision());
        assertEquals(new FullRevision(1, 2, 3, 4), p.getFullRevision());

        assertFalse(p.hasMajorRevision());
        assertNull(p.getMajorRevision());

        assertFalse(p.hasAndroidVersion());
        assertNull(p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertTrue(p.hasMinPlatformToolsRev());
        assertEquals(new FullRevision(5, 6, 7, 8), p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc FullRev=1.2.3 rc4 MinPlatToolsRev=5.6.7 rc8>", p.toString());
    }

    public final void testPkgDescPlatformTool() {
        IPkgDesc p = PkgDesc.newPlatformTool(new FullRevision(1, 2, 3, 4));

        assertEquals(PkgType.PKG_PLATFORM_TOOLS, p.getType());

        assertTrue(p.hasFullRevision());
        assertEquals(new FullRevision(1, 2, 3, 4), p.getFullRevision());

        assertFalse(p.hasMajorRevision());
        assertNull(p.getMajorRevision());

        assertFalse(p.hasAndroidVersion());
        assertNull(p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc FullRev=1.2.3 rc4>", p.toString());

    }

    public final void testPkgDescDoc() throws Exception {
        IPkgDesc p = PkgDesc.newDoc(new AndroidVersion("19"), new MajorRevision(1));

        assertEquals(PkgType.PKG_DOCS, p.getType());

        assertFalse(p.hasFullRevision());
        assertNull(p.getFullRevision());

        assertTrue(p.hasMajorRevision());
        assertEquals(new MajorRevision(1), p.getMajorRevision());

        assertTrue(p.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Android=API 19 MajorRev=1>", p.toString());
    }

    public final void testPkgDescBuildTool() {
        IPkgDesc p = PkgDesc.newBuildTool(new FullRevision(1, 2, 3, 4));

        assertEquals(PkgType.PKG_BUILD_TOOLS, p.getType());

        assertTrue(p.hasFullRevision());
        assertEquals(new FullRevision(1, 2, 3, 4), p.getFullRevision());

        assertFalse(p.hasMajorRevision());
        assertNull(p.getMajorRevision());

        assertFalse(p.hasAndroidVersion());
        assertNull(p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc FullRev=1.2.3 rc4>", p.toString());
    }

    public final void testPkgDescExtra() {
        IPkgDesc p = PkgDesc.newExtra("vendor", "extra_path", new NoPreviewRevision(1, 2, 3));

        assertEquals(PkgType.PKG_EXTRAS, p.getType());

        assertTrue(p.hasFullRevision());
        assertEquals(new FullRevision(1, 2, 3), p.getFullRevision());

        assertFalse(p.hasMajorRevision());
        assertNull(p.getMajorRevision());

        assertFalse(p.hasAndroidVersion());
        assertNull(p.getAndroidVersion());

        assertTrue(p.hasPath());
        assertEquals("vendor/extra_path", p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Path=vendor/extra_path FullRev=1.2.3>", p.toString());
    }

    public final void testPkgDescSource() throws Exception {
        IPkgDesc p = PkgDesc.newSource(new AndroidVersion("19"), new MajorRevision(1));

        assertEquals(PkgType.PKG_SOURCES, p.getType());

        assertFalse(p.hasFullRevision());
        assertNull(p.getFullRevision());

        assertTrue(p.hasMajorRevision());
        assertEquals(new MajorRevision(1), p.getMajorRevision());

        assertTrue(p.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Android=API 19 MajorRev=1>", p.toString());
    }

    public final void testPkgDescSample() throws Exception {
        IPkgDesc p = PkgDesc.newSample(new AndroidVersion("19"),
                                       new MajorRevision(1),
                                       new FullRevision(5, 6, 7, 8));

        assertEquals(PkgType.PKG_SAMPLES, p.getType());

        assertFalse(p.hasFullRevision());
        assertNull(p.getFullRevision());

        assertTrue(p.hasMajorRevision());
        assertEquals(new MajorRevision(1), p.getMajorRevision());

        assertTrue(p.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p.getAndroidVersion());

        assertFalse(p.hasPath());
        assertNull(p.getPath());

        assertTrue(p.hasMinToolsRev());
        assertEquals(new FullRevision(5, 6, 7, 8), p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Android=API 19 MajorRev=1 MinToolsRev=5.6.7 rc8>", p.toString());
    }

    public final void testPkgDescPlatform() throws Exception {
        IPkgDesc p = PkgDesc.newPlatform(new AndroidVersion("19"),
                                         new MajorRevision(1),
                                         new FullRevision(5, 6, 7, 8));

        assertEquals(PkgType.PKG_PLATFORMS, p.getType());

        assertFalse(p.hasFullRevision());
        assertNull(p.getFullRevision());

        assertTrue(p.hasMajorRevision());
        assertEquals(new MajorRevision(1), p.getMajorRevision());

        assertTrue(p.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p.getAndroidVersion());

        assertTrue(p.hasPath());
        assertEquals("android-19", p.getPath());

        assertTrue(p.hasMinToolsRev());
        assertEquals(new FullRevision(5, 6, 7, 8), p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals(
                "<PkgDesc Android=API 19 Path=android-19 MajorRev=1 MinToolsRev=5.6.7 rc8>",
                p.toString());
    }

    public final void testPkgDescAddon() throws Exception {
        IPkgDesc p1 = PkgDesc.newAddon(new AndroidVersion("19"), new MajorRevision(1),
                                       "vendor", "addon_name");

        assertEquals(PkgType.PKG_ADDONS, p1.getType());

        assertFalse(p1.hasFullRevision());
        assertNull(p1.getFullRevision());

        assertTrue(p1.hasMajorRevision());
        assertEquals(new MajorRevision(1), p1.getMajorRevision());

        assertTrue(p1.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p1.getAndroidVersion());

        assertTrue(p1.hasPath());
        assertEquals("vendor:addon_name:19", p1.getPath());

        assertFalse(p1.hasMinToolsRev());
        assertNull(p1.getMinToolsRev());

        assertFalse(p1.hasMinPlatformToolsRev());
        assertNull(p1.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Android=API 19 Path=vendor:addon_name:19 MajorRev=1>",
                     p1.toString());

        // If the add-on hash string isn't determined in the constructor, the implementation
        // should override getPath to compute it lazily when needed.
        IPkgDesc p3 = PkgDesc.newAddon(new AndroidVersion("3"), new MajorRevision(5),
                new PkgDesc.ITargetHashProvider() {
                    @Override
                    public String getTargetHash() {
                        try {
                            return AndroidTargetHash.getAddonHashString(
                                    "vendor3",
                                    "name3",
                                    new AndroidVersion("3"));
                        } catch (AndroidVersionException e) {
                            fail(); // should not happen, it would mean "3" wasn't parsed as a number
                            return null;
                        }
                    }
        });
        assertEquals("vendor3:name3:3", p3.getPath());
    }

    public final void testPkgDescSysImg() throws Exception {
        IPkgDesc p = PkgDesc.newSysImg(new AndroidVersion("19"), "eabi", new MajorRevision(1));

        assertEquals(PkgType.PKG_SYS_IMAGES, p.getType());

        assertFalse(p.hasFullRevision());
        assertNull(p.getFullRevision());

        assertTrue(p.hasMajorRevision());
        assertEquals(new MajorRevision(1), p.getMajorRevision());

        assertTrue(p.hasAndroidVersion());
        assertEquals(new AndroidVersion("19"), p.getAndroidVersion());

        assertTrue(p.hasPath());
        assertEquals("eabi", p.getPath());

        assertFalse(p.hasMinToolsRev());
        assertNull(p.getMinToolsRev());

        assertFalse(p.hasMinPlatformToolsRev());
        assertNull(p.getMinPlatformToolsRev());

        assertEquals("<PkgDesc Android=API 19 Path=eabi MajorRev=1>", p.toString());
    }

}
