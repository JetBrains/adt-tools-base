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

package com.android.sdklib.repository.local;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.io.MockFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.Update.Result;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import java.io.File;

import junit.framework.TestCase;

public class UpdateTest extends TestCase {

    private MockFileOp mFOp;
    private LocalSdk mLS;
    private Multimap<PkgType, RemotePkgInfo> mRemotePkgs;
    private IDescription mSource;

    @Override
    protected void setUp() {
        mFOp = new MockFileOp();
        mLS = new LocalSdk(mFOp);
        mRemotePkgs = TreeMultimap.create();
        mSource = new IDescription() {
            @Override
            public String getShortDescription() {
                return "source";
            }

            @Override
            public String getLongDescription() {
                return "mock sdk repository source";
            }
        };

        mLS.setLocation(new File("/sdk"));
    }

    public final void testComputeUpdates() throws Exception {
        final AndroidVersion api18 = new AndroidVersion("18");
        final AndroidVersion api19 = new AndroidVersion("19");

        addLocalTool("22.3.4", "18");
        addRemoteTool(new FullRevision(23), new FullRevision(19));
        addRemoteTool(new FullRevision(23, 0, 1, 2), new FullRevision(19));

        addLocalPlatformTool("1.0.2");
        addRemotePlatformTool(new FullRevision(1, 0, 3));
        addRemotePlatformTool(new FullRevision(2, 0, 4, 5));

        addLocalBuildTool("18.0.0");
        addLocalBuildTool("19.0.0");
        addRemoteBuildTool(new FullRevision(18, 0, 1));
        addRemoteBuildTool(new FullRevision(19, 1, 2));

        addLocalDoc("18", "1");
        addRemoteDoc(api18, new MajorRevision(2));
        addRemoteDoc(api19, new MajorRevision(3));

        addLocalExtra("18.0.1", "android", "support");
        addLocalExtra("18.0.2", "android", "compat");
        addRemoteExtra(new NoPreviewRevision(18, 3, 4), "android", "support");
        addRemoteExtra(new NoPreviewRevision(18, 5, 6), "android", "compat");
        addRemoteExtra(new NoPreviewRevision(19, 7, 8), "android", "whatever");

        addLocalPlatform("18", "2", "22.1.2");
        addLocalAddOn   ("18", "2", "android", "coolstuff");
        addLocalSource  ("18", "2");
        addLocalSample  ("18", "2", "22.1.2");
        addLocalSysImg  ("18", "2", "eabi");
        addRemotePlatform(api18, new MajorRevision(3), new FullRevision(22));
        addRemoteAddOn   (api18, new MajorRevision(3), "android", "coolstuff");
        addRemoteSource  (api18, new MajorRevision(3));
        addRemoteSample  (api18, new MajorRevision(3), new FullRevision(22));
        addRemoteSysImg  (api18, new MajorRevision(3), "eabi");

        addRemotePlatform(api19, new MajorRevision(4), new FullRevision(23));
        addRemoteAddOn   (api19, new MajorRevision(4), "android", "coolstuff");
        addRemoteSource  (api19, new MajorRevision(4));
        addRemoteSample  (api19, new MajorRevision(4), new FullRevision(23));
        addRemoteSysImg  (api19, new MajorRevision(4), "eabi");

        Result result = Update.computeUpdates(
                mLS.getPkgsInfos(PkgType.PKG_ALL),
                mRemotePkgs);

        assertNotNull(result);
        assertEquals(
                "[<LocalSourcePkgInfo <PkgDesc Type=sources Android=API 18 MajorRev=2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sources Android=API 18 MajorRev=3>>>, " +
                 "<LocalSamplePkgInfo <PkgDesc Type=samples Android=API 18 MajorRev=2 MinToolsRev=22.1.2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=samples Android=API 18 MajorRev=3 MinToolsRev=22.0.0>>>, " +
                  "<LocalAddonPkgInfo <PkgDesc Type=addons Android=API 18 Vendor=android Path=android:coolstuff:18 MajorRev=2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=addons Android=API 18 Vendor=android Path=android:coolstuff:18 MajorRev=3>>>, " +
                 "<LocalSysImgPkgInfo <PkgDesc Type=sys_images Android=API 18 Path=eabi MajorRev=2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=sys_images Android=API 18 Path=eabi MajorRev=3>>>, " +
               "<LocalPlatformPkgInfo <PkgDesc Type=platforms Android=API 18 Path=android-18 MajorRev=2 MinToolsRev=22.1.2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platforms Android=API 18 Path=android-18 MajorRev=3 MinToolsRev=22.0.0>>>, " +
                  "<LocalExtraPkgInfo <PkgDesc Type=extras Vendor=android Path=compat FullRev=18.0.2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extras Vendor=android Path=compat FullRev=18.5.6>>>, " +
                  "<LocalExtraPkgInfo <PkgDesc Type=extras Vendor=android Path=support FullRev=18.0.1> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=extras Vendor=android Path=support FullRev=18.3.4>>>, " +
                    "<LocalDocPkgInfo <PkgDesc Type=docs Android=API 18 MajorRev=1> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=docs Android=API 19 MajorRev=3>>>, " +
           "<LocalPlatformToolPkgInfo <PkgDesc Type=platform_tools FullRev=1.0.2> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=1.0.3>>>, " +
                   "<LocalToolPkgInfo <PkgDesc Type=tools FullRev=22.3.4 MinPlatToolsRev=18.0.0> " +
                         "Updated by: <RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.0 MinPlatToolsRev=19.0.0>>>]",
                result.getUpdatedPkgs().toString());
        assertEquals(
                "[<RemotePkgInfo Source:source <PkgDesc Type=sources Android=API 19 MajorRev=4>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=samples Android=API 19 MajorRev=4 MinToolsRev=23.0.0>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=addons Android=API 19 Vendor=android Path=android:coolstuff:19 MajorRev=4>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=sys_images Android=API 19 Path=eabi MajorRev=4>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=platforms Android=API 19 Path=android-19 MajorRev=4 MinToolsRev=23.0.0>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=extras Vendor=android Path=whatever FullRev=19.7.8>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=18.0.1>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=build_tools FullRev=19.1.2>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=platform_tools FullRev=2.0.4 rc5>>, " +
                 "<RemotePkgInfo Source:source <PkgDesc Type=tools FullRev=23.0.1 rc2 MinPlatToolsRev=19.0.0>>]",
                result.getNewPkgs().toString());
    }

    //---

    private void addLocalTool(String fullRev, String minPlatToolsRev) {
        mFOp.recordExistingFolder("/sdk/tools");
        mFOp.recordExistingFile("/sdk/tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Platform.MinPlatformToolsRev=" + minPlatToolsRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mFOp.recordExistingFile("/sdk/tools/" + SdkConstants.androidCmdName(), "placeholder");
        mFOp.recordExistingFile("/sdk/tools/" + SdkConstants.FN_EMULATOR, "placeholder");
    }

    private void addLocalPlatformTool(String fullRev) {
        mFOp.recordExistingFolder("/sdk/platform-tools");
        mFOp.recordExistingFile("/sdk/platform-tools/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
    }

    private void addLocalDoc(String api, String majorRev) {
        mFOp.recordExistingFolder("/sdk/docs");
        mFOp.recordExistingFile("/sdk/docs/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
        mFOp.recordExistingFile("/sdk/docs/index.html", "placeholder");
    }

    private void addLocalBuildTool(String fullRev) {
        mFOp.recordExistingFolder("/sdk/build-tools");
        mFOp.recordExistingFolder("/sdk/build-tools/" + fullRev);
        mFOp.recordExistingFile("/sdk/build-tools/" + fullRev + "/source.properties",
                "Pkg.License=Terms and Conditions\n" +
                "Archive.Os=WINDOWS\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n" +
                "Pkg.SourceUrl=https\\://example.com/repository-8.xml");
    }

    private void addLocalExtra(String fullRev, String vendor, String path) {
        mFOp.recordExistingFolder("/sdk/extras");
        mFOp.recordExistingFolder("/sdk/extras/" + vendor);
        mFOp.recordExistingFolder("/sdk/extras/" + vendor + "/" + path);
        mFOp.recordExistingFile("/sdk/extras/" + vendor + "/" + path + "/source.properties",
                "Extra.NameDisplay=Android Support Library\n" +
                "Extra.VendorDisplay=" + vendor + "\n" +
                "Extra.VendorId=" + vendor + "\n" +
                "Extra.Path=" + path +  "\n" +
                "Extra.OldPaths=compatibility\n" +
                "Archive.Os=ANY\n" +
                "Pkg.Revision=" + fullRev + "\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSource(String api, String majorRev) {
        mFOp.recordExistingFolder("/sdk/sources");
        mFOp.recordExistingFolder("/sdk/sources/android-" + api);
        mFOp.recordExistingFile("/sdk/sources/android-" + api + "/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.CodeName=\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSample(String api, String majorRev, String minToolsRev) {
        mFOp.recordExistingFolder("/sdk/samples");
        mFOp.recordExistingFolder("/sdk/samples/android-" + api);
        mFOp.recordExistingFile("/sdk/samples/android-" + api + "/source.properties",
                "Archive.Os=ANY\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.CodeName=\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Platform.MinToolsRev=" + minToolsRev + "\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalSysImg(String api, String majorRev, String abi) {
        mFOp.recordExistingFolder("/sdk/system-images");
        mFOp.recordExistingFolder("/sdk/system-images/android-" + api);
        mFOp.recordExistingFolder("/sdk/system-images/android-" + api + "/" + abi);
        mFOp.recordExistingFile("/sdk/system-images/android-" + api + "/" + abi +"/source.properties",
                "SystemImage.Abi=" + abi + "\n" +
                "Pkg.Revision=" + majorRev + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
    }

    private void addLocalPlatform(String api, String majorRev, String minToolsRev) {
        mFOp.recordExistingFolder("/sdk/platforms");
        mFOp.recordExistingFolder("/sdk/platforms/android-" + api);
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/android.jar");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/framework.aidl");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/source.properties",
                "Pkg.Revision=" + majorRev + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "AndroidVersion.ApiLevel=18\n" +
                "Layoutlib.Api=10\n" +
                "Layoutlib.Revision=1\n" +
                "Platform.MinToolsRev=" + minToolsRev + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/sdk.properties",
                "sdk.ant.templates.revision=1\n" +
                "sdk.skin.default=WVGA800\n");
        mFOp.recordExistingFile("/sdk/platforms/android-" + api + "/build.prop",
                "ro.build.id=JB_MR2\n" +
                "ro.build.display.id=sdk-eng 4.3 JB_MR2 819563 test-keys\n" +
                "ro.build.version.incremental=819563\n" +
                "ro.build.version.sdk=" + api + "\n" +
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
    }

    private void addLocalAddOn(String api, String majorRev, String vendor, String name) {
        String addon_dir = "addon-" + vendor + "-" + name;
        mFOp.recordExistingFolder("/sdk/add-ons");
        mFOp.recordExistingFolder("/sdk/add-ons/" + addon_dir);
        mFOp.recordExistingFile("/sdk/add-ons/" + addon_dir + "/source.properties",
                "Pkg.Revision=" + majorRev + "\n" +
                "Addon.VendorId=" + vendor + "\n" +
                "Addon.VendorDisplay=" + vendor + "\n" +
                "Addon.NameId=" + name + "\n" +
                "Addon.NameDisplay=" + name + "\n" +
                "AndroidVersion.ApiLevel=" + api + "\n" +
                "Pkg.LicenseRef=android-sdk-license\n" +
                "Archive.Os=ANY\n" +
                "Archive.Arch=ANY\n");
        mFOp.recordExistingFile("/sdk/add-ons/" + addon_dir + "/manifest.ini",
                "Pkg.Revision=" + majorRev + "\n" +
                "name=" + name + "\n" +
                "name-id=" + name + "\n" +
                "vendor=" + vendor + "\n" +
                "vendor-id=" + vendor + "\n" +
                "api=" + api + "\n" +
                "libraries=com.foo.lib1;com.blah.lib2\n" +
                "com.foo.lib1=foo.jar;API for Foo\n" +
                "com.blah.lib2=blah.jar;API for Blah\n");
    }

    //---

    private void addRemoteTool(FullRevision revision, FullRevision minPlatformToolsRev) {
        IPkgDesc d = PkgDesc.newTool(revision, minPlatformToolsRev);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemotePlatformTool(FullRevision revision) {
        IPkgDesc d = PkgDesc.newPlatformTool(revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteDoc(AndroidVersion version, MajorRevision revision) {
        IPkgDesc d = PkgDesc.newDoc(version, revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteBuildTool(FullRevision revision) {
        IPkgDesc d = PkgDesc.newBuildTool(revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteExtra(NoPreviewRevision revision, String vendor, String path) {
        IPkgDesc d = PkgDesc.newExtra(vendor, path, null, revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSource(AndroidVersion version, MajorRevision revision) {
        IPkgDesc d = PkgDesc.newSource(version, revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSample(AndroidVersion version,
                                 MajorRevision revision,
                                 FullRevision minToolsRev) {
        IPkgDesc d = PkgDesc.newSample(version, revision, minToolsRev);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteSysImg(AndroidVersion version, MajorRevision revision, String abi) {
        IPkgDesc d = PkgDesc.newSysImg(version, abi, revision);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemotePlatform(AndroidVersion version,
                                   MajorRevision revision,
                                   FullRevision minToolsRev) {
        IPkgDesc d = PkgDesc.newPlatform(version, revision, minToolsRev);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

    private void addRemoteAddOn(AndroidVersion version,
                                MajorRevision revision,
                                String vendor,
                                String name) {
        IPkgDesc d = PkgDesc.newAddon(version, revision, vendor, name);
        RemotePkgInfo r = new RemotePkgInfo(d, mSource);
        mRemotePkgs.put(d.getType(), r);
    }

}
