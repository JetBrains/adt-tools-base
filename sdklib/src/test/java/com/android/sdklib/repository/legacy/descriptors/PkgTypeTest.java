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

package com.android.sdklib.repository.legacy.descriptors;

import com.android.sdklib.repository.legacy.descriptors.PkgType;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class PkgTypeTest extends TestCase {

    public final void testPkgTypeTool() {
        PkgType p = PkgType.PKG_TOOLS;
        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertTrue (p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypePlatformTool() {
        PkgType p = PkgType.PKG_PLATFORM_TOOLS;
        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeDoc() {
        PkgType p = PkgType.PKG_DOC;
        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeBuildTool() {
        PkgType p = PkgType.PKG_BUILD_TOOLS;

        assertFalse(p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeExtra() {
        PkgType p = PkgType.PKG_EXTRA;

        assertFalse(p.hasAndroidVersion());
        assertTrue (p.hasPath());
        assertFalse(p.hasTag());
        assertTrue (p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSource() throws Exception {
        PkgType p = PkgType.PKG_SOURCE;

        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSample() throws Exception {
        PkgType p = PkgType.PKG_SAMPLE;

        assertTrue (p.hasAndroidVersion());
        assertFalse(p.hasPath());
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertTrue (p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypePlatform() throws Exception {
        PkgType p = PkgType.PKG_PLATFORM;

        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // platform path is its hash string
        assertFalse(p.hasTag());
        assertFalse(p.hasVendor());
        assertTrue (p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeAddon() throws Exception {
        PkgType p = PkgType.PKG_ADDON;

        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // add-on path is its hash string
        assertFalse(p.hasTag());
        assertTrue (p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeSysImg() throws Exception {
        PkgType p = PkgType.PKG_SYS_IMAGE;

        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // sys-img path is its ABI string
        assertTrue (p.hasTag());
        assertFalse(p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgTypeAddonSysImg() throws Exception {
        PkgType p = PkgType.PKG_ADDON_SYS_IMAGE;

        assertTrue (p.hasAndroidVersion());
        assertTrue (p.hasPath());               // sys-img path is its ABI string
        assertTrue (p.hasTag());
        assertTrue (p.hasVendor());
        assertFalse(p.hasMinToolsRev());
        assertFalse(p.hasMinPlatformToolsRev());
    }

    public final void testPkgType_UniqueIntValues() {
        // Check all types have a unique int value
        Map<Integer, PkgType> ints = new HashMap<Integer, PkgType>();
        for (PkgType type : PkgType.values()) {
            Integer i = type.getIntValue();
            if (ints.containsKey(i)) {
                fail(String.format("Int value 0x%04x defined by both PkgType.%s and PkgType.%s",
                        i, type, ints.get(i)));
            }
            ints.put(i, type);
        }
    }

}
