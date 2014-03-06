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

import junit.framework.TestCase;

public class PkgTypeTest extends TestCase {

    public final void testPkgTypeTool() {
        IPkgCapabilities p = PkgType.PKG_TOOLS;
        assertFalse(p.hasMajorRevision());
        assertTrue (p.hasFullRevision());
        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertTrue (p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypePlatformTool() {
        IPkgCapabilities p = PkgType.PKG_PLATFORM_TOOLS;
        assertFalse(p.hasMajorRevision());
        assertTrue (p.hasFullRevision());
        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeDoc() {
        IPkgCapabilities p = PkgType.PKG_DOCS;
        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeBuildTool() {
        IPkgCapabilities p = PkgType.PKG_BUILD_TOOLS;

        assertTrue (p.hasFullRevision());
        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeExtra() {
        IPkgCapabilities p = PkgType.PKG_EXTRAS;

        assertFalse(p.hasMajorRevision());
        assertTrue (p.hasFullRevision());
        assertFalse(p.hasAndroidVersion());
        assertTrue (p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSource() throws Exception {
        IPkgCapabilities p = PkgType.PKG_SOURCES;

        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSample() throws Exception {
        IPkgCapabilities p = PkgType.PKG_SAMPLES;

        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertTrue (p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypePlatform() throws Exception {
        IPkgCapabilities p = PkgType.PKG_PLATFORMS;

        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // platform path is its hash string
        assertTrue (p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeAddon() throws Exception {
        IPkgCapabilities p = PkgType.PKG_ADDONS;

        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // add-on path is its hash string
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSysImg() throws Exception {
        IPkgCapabilities p = PkgType.PKG_SYS_IMAGES;

        assertTrue (p.hasMajorRevision());
        assertFalse(p.hasFullRevision());
        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // sys-img path is its ABI string
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

}
