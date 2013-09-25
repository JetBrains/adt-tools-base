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

package com.android.sdklib.local;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.BuildToolInfo.PathId;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.repository.archives.Archive;
import com.android.sdklib.internal.repository.packages.Package;
import com.android.sdklib.io.MockFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;

import java.io.File;
import java.util.Arrays;

import junit.framework.TestCase;

@SuppressWarnings("MethodMayBeStatic")
public class LocalSdkTest extends TestCase {

    public final void testLocalSdkTest_getLocation() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        assertNull(ls.getLocation());
        ls.setLocation(new File("/sdk"));
        assertEquals(new File("/sdk"), ls.getLocation());
    }

    public final void testLocalSdkTest_getPkgInfo_Tools() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertNull(ls.getPkgInfo(LocalSdk.PKG_TOOLS));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/tools");
        fop.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=22.3.4\n" +
                "Platform.MinPlatformToolsRev=18\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");
        fop.recordExistingFile("/sdk/tools/" + SdkConstants.androidCmdName(), "placeholder");
        fop.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_TOOLS);
        assertNotNull(pi);
        assertTrue(pi instanceof LocalToolPkgInfo);
        assertEquals(new File("/sdk/tools"), pi.getLocalDir());
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new FullRevision(22, 3, 4), pi.getFullRevision());
        assertEquals("<LocalToolPkgInfo FullRev=22.3.4>", pi.toString());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);
        assertEquals(new FullRevision(22, 3, 4), pkg.getRevision());
        assertEquals("Android SDK Tools, revision 22.3.4", pkg.getShortDescription());
        assertTrue(pkg.isLocal());
        Archive a = pkg.getArchives()[0];
        assertTrue(a.isLocal());
        assertEquals("/sdk/tools", fop.getAgnosticAbsPath(a.getLocalOsPath()));
    }

    public final void testLocalSdkTest_getPkgInfo_PlatformTools() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertNull(ls.getPkgInfo(LocalSdk.PKG_PLATFORM_TOOLS));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/platform-tools");
        fop.recordExistingFile("/sdk/platform-tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=18.19.20\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_PLATFORM_TOOLS);
        assertNotNull(pi);
        assertTrue(pi instanceof LocalPlatformToolPkgInfo);
        assertEquals(new File("/sdk/platform-tools"), pi.getLocalDir());
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new FullRevision(18, 19, 20), pi.getFullRevision());
        assertEquals("<LocalPlatformToolPkgInfo FullRev=18.19.20>", pi.toString());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);
    }

    public final void testLocalSdkTest_getPkgInfo_Docs() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertNull(ls.getPkgInfo(LocalSdk.PKG_DOCS));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/docs");
        fop.recordExistingFile("/sdk/docs/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.Revision=2\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");
        fop.recordExistingFile("/sdk/docs/index.html", "placeholder");

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_DOCS);
        assertNotNull(pi);
        assertTrue(pi instanceof LocalDocPkgInfo);
        assertEquals(new File("/sdk/docs"), pi.getLocalDir());
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new MajorRevision(2), pi.getMajorRevision());
        assertEquals("<LocalDocPkgInfo MajorRev=2>", pi.toString());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);
        assertEquals(new MajorRevision(2), pkg.getRevision());
        assertEquals("Documentation for Android SDK, API 18, revision 2", pkg.getShortDescription());
        assertTrue(pkg.isLocal());
        Archive a = pkg.getArchives()[0];
        assertTrue(a.isLocal());
        assertEquals("/sdk/docs", fop.getAgnosticAbsPath(a.getLocalOsPath()));
    }

    public final void testLocalSdkTest_getPkgInfo_BuildTools() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_BUILD_TOOLS)));

        // We haven't defined any mock build-tools so the API will return
        // a legacy build-tools based on top of platform tools if there's one with
        // a revision < 17.
        fop.recordExistingFolder("/sdk/platform-tools");
        fop.recordExistingFile("/sdk/platform-tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=16\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");

        // -- get latest build tool in legacy/compatibility mode

        BuildToolInfo bt = ls.getLatestBuildTool();
        assertNotNull(bt);
        assertEquals(new FullRevision(16), bt.getRevision());
        assertEquals(new File("/sdk/platform-tools"), bt.getLocation());
        assertEquals("/sdk/platform-tools/" + SdkConstants.FN_AAPT,
                     fop.getAgnosticAbsPath(bt.getPath(PathId.AAPT)));

        // clearing local packages also clears the legacy build-tools
        ls.clearLocalPkg(LocalSdk.PKG_ALL);

        // setup fake files
        fop.recordExistingFolder("/sdk/build-tools");
        fop.recordExistingFolder("/sdk/build-tools/17");
        fop.recordExistingFolder("/sdk/build-tools/18.1.2");
        fop.recordExistingFolder("/sdk/build-tools/12.2.3");
        fop.recordExistingFile("/sdk/build-tools/17/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=17\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");
        fop.recordExistingFile("/sdk/build-tools/18.1.2/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=18.1.2\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");
        fop.recordExistingFile("/sdk/build-tools/12.2.3/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=12.2.3\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://dl-ssl.google.com/android/repository/repository-8.xml");

        // -- get latest build tool 18.1.2

        BuildToolInfo bt18a = ls.getLatestBuildTool();
        assertNotNull(bt18a);
        assertEquals(new FullRevision(18, 1, 2), bt18a.getRevision());
        assertEquals(new File("/sdk/build-tools/18.1.2"), bt18a.getLocation());
        assertEquals("/sdk/build-tools/18.1.2/" + SdkConstants.FN_AAPT,
                     fop.getAgnosticAbsPath(bt18a.getPath(PathId.AAPT)));

        // -- get specific build tools by version

        BuildToolInfo bt18b = ls.getBuildTool(new FullRevision(18, 1, 2));
        assertSame(bt18a, bt18b);

        BuildToolInfo bt17 = ls.getBuildTool(new FullRevision(17));
        assertNotNull(bt17);
        assertEquals(new FullRevision(17), bt17.getRevision());
        assertEquals(new File("/sdk/build-tools/17"), bt17.getLocation());
        assertEquals("/sdk/build-tools/17/" + SdkConstants.FN_AAPT,
                     fop.getAgnosticAbsPath(bt17.getPath(PathId.AAPT)));

        assertNull(ls.getBuildTool(new FullRevision(0)));
        assertNull(ls.getBuildTool(new FullRevision(16, 17, 18)));

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_BUILD_TOOLS, new FullRevision(18, 1, 2));
        assertNotNull(pi);
        assertTrue(pi instanceof LocalBuildToolPkgInfo);
        assertSame(bt18a, ((LocalBuildToolPkgInfo)pi).getBuildToolInfo());
        assertEquals(new File("/sdk/build-tools/18.1.2"), pi.getLocalDir());
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new FullRevision(18, 1, 2), pi.getFullRevision());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);

        // -- get all build-tools and iterate, sorted by revision.

        assertEquals("[<LocalBuildToolPkgInfo FullRev=12.2.3>, " +
                      "<LocalBuildToolPkgInfo FullRev=17.0.0>, " +
                      "<LocalBuildToolPkgInfo FullRev=18.1.2>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_BUILD_TOOLS)));
    }

    public final void testLocalSdkTest_getPkgInfo_Extra() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_EXTRAS)));
        assertNull(ls.getPkgInfo(LocalSdk.PKG_EXTRAS, "vendor1/path1"));
        assertNull(ls.getExtra("vendor1/path1"));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/extras");
        fop.recordExistingFolder("/sdk/extras/vendor1");
        fop.recordExistingFolder("/sdk/extras/vendor1/path1");
        fop.recordExistingFolder("/sdk/extras/vendor1/path2");
        fop.recordExistingFolder("/sdk/extras/vendor2");
        fop.recordExistingFolder("/sdk/extras/vendor2/path1");
        fop.recordExistingFolder("/sdk/extras/vendor2/path2");
        fop.recordExistingFolder("/sdk/extras/vendor3");
        fop.recordExistingFolder("/sdk/extras/vendor3/path3");
        fop.recordExistingFile("/sdk/extras/vendor1/path1/source.properties",
                "Extra.NameDisplay=Android Support Library\n" +
                "Extra.VendorDisplay=Vendor\n" +
                "Extra.VendorId=vendor1\n" +
                "Extra.Path=path1\n" +
                "Extra.OldPaths=compatibility\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=11\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/extras/vendor1/path2/source.properties",
                "Extra.NameDisplay=Some Extra\n" +
                "Extra.VendorDisplay=Some Vendor\n" +
                "Extra.VendorId=vendor1\n" +
                "Extra.Path=path2\n" +
                "Archive.Os=ANY\n" +
                "Pkg.Revision=21\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/extras/vendor2/path1/source.properties",
                "Extra.NameDisplay=Another Extra\n" +
                "Extra.VendorDisplay=Another Vendor\n" +
                "Extra.VendorId=vendor2\n" +
                "Extra.Path=path1\n" +
                "Extra.OldPaths=compatibility\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=21\n" +
                "Archive.Arch=ANY\n");

        LocalPkgInfo pi1 = ls.getPkgInfo(LocalSdk.PKG_EXTRAS, "vendor1/path1");
        assertNotNull(pi1);
        assertTrue(pi1 instanceof LocalExtraPkgInfo);
        assertEquals("vendor1/path1", ((LocalExtraPkgInfo)pi1).getPath());
        assertEquals("path1",         ((LocalExtraPkgInfo)pi1).getExtraPath());
        assertEquals("vendor1",       ((LocalExtraPkgInfo)pi1).getVendorId());
        assertEquals(new File("/sdk/extras/vendor1/path1"), pi1.getLocalDir());
        assertSame(ls, pi1.getLocalSdk());
        assertEquals(null, pi1.getLoadError());
        assertEquals(new FullRevision(11), pi1.getFullRevision());

        Package pkg = pi1.getPackage();
        assertNotNull(pkg);

        LocalExtraPkgInfo pi2 = ls.getExtra("vendor1/path1");
        assertSame(pi1, pi2);

        // -- get all extras and iterate, sorted by revision.

        assertEquals("[<LocalExtraPkgInfo Path=vendor1/path1 FullRev=11.0.0>, " +
                      "<LocalExtraPkgInfo Path=vendor1/path2 FullRev=21.0.0>, " +
                      "<LocalExtraPkgInfo Path=vendor2/path1 FullRev=21.0.0>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_EXTRAS)));
    }

    public final void testLocalSdkTest_getPkgInfo_Sources() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SOURCES)));
        assertNull(ls.getPkgInfo(LocalSdk.PKG_SOURCES, new AndroidVersion(18, null)));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/sources");
        fop.recordExistingFolder("/sdk/sources/android-CUPCAKE");
        fop.recordExistingFolder("/sdk/sources/android-18");
        fop.recordExistingFolder("/sdk/sources/android-42");
        fop.recordExistingFile("/sdk/sources/android-CUPCAKE/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=3\n" +
                "AndroidVersion.CodeName=CUPCAKE\n" +
                "Pkg.Revision=1\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/sources/android-18/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.Revision=2\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/sources/android-42/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=42\n" +
                "Pkg.Revision=3\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");

        LocalPkgInfo pi18 = ls.getPkgInfo(LocalSdk.PKG_SOURCES, new AndroidVersion(18, null));
        assertNotNull(pi18);
        assertTrue(pi18 instanceof LocalSourcePkgInfo);
        assertSame(ls, pi18.getLocalSdk());
        assertEquals(null, pi18.getLoadError());
        assertEquals(new AndroidVersion(18, null), pi18.getAndroidVersion());
        assertEquals(new MajorRevision(2), pi18.getMajorRevision());

        Package pkg = pi18.getPackage();
        assertNotNull(pkg);

        LocalPkgInfo pi1 = ls.getPkgInfo(LocalSdk.PKG_SOURCES, new AndroidVersion(3, "CUPCAKE"));
        assertNotNull(pi1);
        assertEquals(new AndroidVersion(3, "CUPCAKE"), pi1.getAndroidVersion());
        assertEquals(new MajorRevision(1), pi1.getMajorRevision());

        // -- get all extras and iterate, sorted by revision.

        assertEquals("[<LocalSourcePkgInfo Android=API 3, CUPCAKE preview MajorRev=1>, " +
                      "<LocalSourcePkgInfo Android=API 18 MajorRev=2>, " +
                      "<LocalSourcePkgInfo Android=API 42 MajorRev=3>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SOURCES)));
    }

    public final void testLocalSdkTest_getPkgInfo_Samples() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SAMPLES)));
        assertNull(ls.getPkgInfo(LocalSdk.PKG_SAMPLES, new AndroidVersion(18, null)));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/samples");
        fop.recordExistingFolder("/sdk/samples/android-18");
        fop.recordExistingFolder("/sdk/samples/android-42");
        fop.recordExistingFile("/sdk/samples/android-18/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.Revision=2\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/samples/android-42/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=42\n" +
                "Pkg.Revision=3\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");

        LocalPkgInfo pi18 = ls.getPkgInfo(LocalSdk.PKG_SAMPLES, new AndroidVersion(18, null));
        assertNotNull(pi18);
        assertTrue(pi18 instanceof LocalSamplePkgInfo);
        assertSame(ls, pi18.getLocalSdk());
        assertEquals(null, pi18.getLoadError());
        assertEquals(new AndroidVersion(18, null), pi18.getAndroidVersion());
        assertEquals(new MajorRevision(2), pi18.getMajorRevision());

        Package pkg = pi18.getPackage();
        assertNotNull(pkg);

        // -- get all extras and iterate, sorted by revision.

        assertEquals("[<LocalSamplePkgInfo Android=API 18 MajorRev=2>, " +
                      "<LocalSamplePkgInfo Android=API 42 MajorRev=3>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SAMPLES)));
    }

    public final void testLocalSdkTest_getPkgInfo_SysImages() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SYS_IMAGES)));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        fop.recordExistingFolder("/sdk/system-images");
        fop.recordExistingFolder("/sdk/system-images/android-18");
        fop.recordExistingFolder("/sdk/system-images/android-18/armeabi-v7a");
        fop.recordExistingFolder("/sdk/system-images/android-18/x86");
        fop.recordExistingFolder("/sdk/system-images/android-42");
        fop.recordExistingFolder("/sdk/system-images/android-42/x86");
        fop.recordExistingFolder("/sdk/system-images/android-42/mips");
        fop.recordExistingFile("/sdk/system-images/android-18/armeabi-v7a/source.properties",
                "Pkg.Revision=1\n" +
                "SystemImage.Abi=armeabi-v7a\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/system-images/android-18/x86/source.properties",
                "Pkg.Revision=2\n" +
                "SystemImage.Abi=x86\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/system-images/android-42/x86/source.properties",
                "Pkg.Revision=3\n" +
                "SystemImage.Abi=x86\n" +
                "AndroidVersion.ApiLevel=42\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/system-images/android-42/mips/source.properties",
                "Pkg.Revision=4\n" +
                "SystemImage.Abi=mips\n" +
                "AndroidVersion.ApiLevel=42\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");

        assertEquals("[<LocalSysImgPkgInfo Android=API 18 Path=armeabi-v7a MajorRev=1>, " +
                      "<LocalSysImgPkgInfo Android=API 18 Path=x86 MajorRev=2>, " +
                      "<LocalSysImgPkgInfo Android=API 42 Path=mips MajorRev=4>, " +
                      "<LocalSysImgPkgInfo Android=API 42 Path=x86 MajorRev=3>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_SYS_IMAGES)));

        LocalPkgInfo pi = ls.getPkgsInfos(LocalSdk.PKG_SYS_IMAGES)[0];
        assertNotNull(pi);
        assertTrue(pi instanceof LocalSysImgPkgInfo);
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new MajorRevision(1), pi.getMajorRevision());
        assertEquals("armeabi-v7a", pi.getPath());

        Package pkg = pi.getPackage();
        assertNull(pkg);
    }

    public final void testLocalSdkTest_getPkgInfo_Platforms() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_PLATFORMS)));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        recordPlatform18(fop);

        assertEquals("[<LocalPlatformPkgInfo Android=API 18 Path=android-18 MajorRev=1>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_PLATFORMS)));

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_PLATFORMS, new AndroidVersion(18, null));
        assertNotNull(pi);
        assertTrue(pi instanceof LocalPlatformPkgInfo);
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new AndroidVersion(18, null), pi.getAndroidVersion());
        assertEquals(new MajorRevision(1), pi.getMajorRevision());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);

        IAndroidTarget t1 = ((LocalPlatformPkgInfo)pi).getAndroidTarget();
        assertNotNull(t1);

        LocalPkgInfo pi2 = ls.getPkgInfo(LocalSdk.PKG_PLATFORMS, "android-18");
        assertSame(pi, pi2);

        IAndroidTarget t2 = ls.getTargetFromHashString("android-18");
        assertSame(t1, t2);
    }

    public final void testLocalSdkTest_getPkgInfo_Addons() {
        MockFileOp fop = new MockFileOp();
        LocalSdk ls = new LocalSdk(fop);
        ls.setLocation(new File("/sdk"));

        // check empty
        assertEquals("[]", Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_ADDONS)));

        // setup fake files
        ls.clearLocalPkg(LocalSdk.PKG_ALL);
        recordPlatform18(fop);
        fop.recordExistingFolder("/sdk/add-ons");
        fop.recordExistingFolder("/sdk/add-ons/addon-vendor_name-2");
        fop.recordExistingFile("/sdk/add-ons/addon-vendor_name-2/source.properties",
                "Pkg.Revision=2\n" +
                "Addon.VendorId=vendor\n" +
                "Addon.VendorDisplay=Some Vendor\n" +
                "Addon.NameId=name\n" +
                "Addon.NameDisplay=Some Name\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/add-ons/addon-vendor_name-2/manifest.ini",
                "revision=2\n" +
                "name=Some Name\n" +
                "name-id=name\n" +
                "vendor=Some Vendor\n" +
                "vendor-id=vendor\n" +
                "api=18\n" +
                "libraries=com.foo.lib1;com.blah.lib2\n" +
                "com.foo.lib1=foo.jar;API for Foo\n" +
                "com.blah.lib2=blah.jar;API for Blah\n");

        assertEquals("[<LocalAddonPkgInfo Android=API 18 Path=Some Vendor:Some Name:18 MajorRev=2>]",
                     Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_ADDONS)));
        assertEquals("[<LocalPlatformPkgInfo Android=API 18 Path=android-18 MajorRev=1>, " +
                      "<LocalAddonPkgInfo Android=API 18 Path=Some Vendor:Some Name:18 MajorRev=2>]",
                    Arrays.toString(ls.getPkgsInfos(LocalSdk.PKG_ALL)));

        LocalPkgInfo pi = ls.getPkgInfo(LocalSdk.PKG_ADDONS, "Some Vendor:Some Name:18");
        assertNotNull(pi);
        assertTrue(pi instanceof LocalAddonPkgInfo);
        assertSame(ls, pi.getLocalSdk());
        assertEquals(null, pi.getLoadError());
        assertEquals(new AndroidVersion(18, null), pi.getAndroidVersion());
        assertEquals(new MajorRevision(2), pi.getMajorRevision());
        assertEquals("Some Vendor:Some Name:18", pi.getPath());

        Package pkg = pi.getPackage();
        assertNotNull(pkg);

        IAndroidTarget t = ls.getTargetFromHashString("Some Vendor:Some Name:18");
        assertSame(t, ((LocalAddonPkgInfo) pi).getAndroidTarget());
        assertNotNull(t);

    }

    //-----

    private void recordPlatform18(MockFileOp fop) {
        fop.recordExistingFolder("/sdk/platforms");
        fop.recordExistingFolder("/sdk/platforms/android-18");
        fop.recordExistingFile("/sdk/platforms/android-18/android.jar");
        fop.recordExistingFile("/sdk/platforms/android-18/framework.aidl");
        fop.recordExistingFile("/sdk/platforms/android-18/source.properties",
                "Pkg.Revision=1\n" +
                "Platform.Version=4.3\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Layoutlib.Api=10\n" +
                "Layoutlib.Revision=1\n" +
                "Platform.MinToolsRev=21\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        fop.recordExistingFile("/sdk/platforms/android-18/sdk.properties",
                "sdk.ant.templates.revision=1\n" +
                "sdk.skin.default=WVGA800\n");
        fop.recordExistingFile("/sdk/platforms/android-18/build.prop",
                "ro.build.id=JB_MR2\n" +
                "ro.build.display.id=sdk-eng 4.3 JB_MR2 819563 test-keys\n" +
                "ro.build.version.incremental=819563\n" +
                "ro.build.version.sdk=18\n" +
                "ro.build.version.codename=REL\n" +
                "ro.build.version.release=4.3\n" +
                "ro.build.date=Tue Sep 10 18:43:31 UTC 2013\n" +
                "ro.build.date.utc=1378838611\n" +
                "ro.build.type=eng\n" +
                "ro.build.tags=test-keys\n" +
                "ro.product.model=sdk\n" +
                "ro.product.name=sdk\n" +
                "ro.product.board=\n" +
                "ro.product.cpu.abi=armeabi-v7a\n" +
                "ro.product.cpu.abi2=armeabi\n" +
                "ro.product.locale.language=en\n" +
                "ro.product.locale.region=US\n" +
                "ro.wifi.channels=\n" +
                "ro.board.platform=\n" +
                "# ro.build.product is obsolete; use ro.product.device\n" +
                "# Do not try to parse ro.build.description or .fingerprint\n" +
                "ro.build.description=sdk-eng 4.3 JB_MR2 819563 test-keys\n" +
                "ro.build.fingerprint=generic/sdk/generic:4.3/JB_MR2/819563:eng/test-keys\n" +
                "ro.build.characteristics=default\n" +
                "rild.libpath=/system/lib/libreference-ril.so\n" +
                "rild.libargs=-d /dev/ttyS0\n" +
                "ro.config.notification_sound=OnTheHunt.ogg\n" +
                "ro.config.alarm_alert=Alarm_Classic.ogg\n" +
                "ro.kernel.android.checkjni=1\n" +
                "xmpp.auto-presence=true\n" +
                "ro.config.nocheckin=yes\n" +
                "net.bt.name=Android\n" +
                "dalvik.vm.stack-trace-file=/data/anr/traces.txt\n" +
                "ro.build.user=generic\n" +
                "ro.build.host=generic\n" +
                "ro.product.brand=generic\n" +
                "ro.product.manufacturer=generic\n" +
                "ro.product.device=generic\n" +
                "ro.build.product=generic\n");
    }}
