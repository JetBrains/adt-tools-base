/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sdklib.repository.targets;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.RepoFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for {@link AndroidTargetManager}.
 */
public class AndroidTargetManagerTest extends TestCase {

    public void testNew() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform13(fop);
        recordPlatform23(fop);
        recordGoogleApisAddon23(fop);
        recordGoogleTvAddon13(fop);
        recordBuildTool23(fop);
        recordSysImg13(fop);
        recordGoogleApisSysImg23(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();

        AndroidTargetManager mgr = handler.getAndroidTargetManager(progress);
        Collection<IAndroidTarget> targets = mgr.getTargets(progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals(4, targets.size());
        Iterator<IAndroidTarget> iter = targets.iterator();

        IAndroidTarget addon13 = iter.next();
        IAndroidTarget platform13 = iter.next();
        verifyPlatform13(platform13, fop);
        verifyAddon13(addon13, platform13, fop);
        IAndroidTarget addon23 = iter.next();
        IAndroidTarget platform23 = iter.next();
        verifyPlatform23(platform23, fop);
        verifyAddon23(addon23, platform23, fop);
    }

    public void testLegacyAddon() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform23(fop);
        recordLegacyGoogleApis23(fop);
        recordBuildTool23(fop);
        recordGoogleApisSysImg23(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager mgr = handler.getAndroidTargetManager(progress);
        Collection<IAndroidTarget> targets = mgr.getTargets(progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, targets.size());
        Iterator<IAndroidTarget> iter = targets.iterator();

        IAndroidTarget addon23 = iter.next();
        IAndroidTarget platform23 = iter.next();
        verifyPlatform23(platform23, fop);
        verifyAddon23(addon23, platform23, fop);
    }

    public void testInstalledLegacyAddon() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform23(fop);
        recordInstalledLegacyGoogleApis23(fop);
        recordBuildTool23(fop);
        recordGoogleApisSysImg23(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager mgr = handler.getAndroidTargetManager(progress);
        Collection<IAndroidTarget> targets = mgr.getTargets(progress);
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, targets.size());
        Iterator<IAndroidTarget> iter = targets.iterator();

        IAndroidTarget addon23 = iter.next();
        IAndroidTarget platform23 = iter.next();
        verifyAddon23(addon23, platform23, fop);
        verifyPlatform23(platform23, fop);
    }

    public void testSources() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform23(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager mgr = handler.getAndroidTargetManager(progress);
        IAndroidTarget target = mgr.getTargets(progress).iterator().next();
        progress.assertNoErrorsOrWarnings();
        String sourcesPath = target.getPath(IAndroidTarget.SOURCES);
        assertEquals("/sdk/platforms/android-23/sources", fop.getAgnosticAbsPath(sourcesPath));

        recordSources23(fop);
        handler.getSdkManager(progress).loadSynchronously(0, progress, null, null);
        mgr = handler.getAndroidTargetManager(progress);
        target = mgr.getTargets(progress).iterator().next();
        progress.assertNoErrorsOrWarnings();
        sourcesPath = target.getPath(IAndroidTarget.SOURCES);
        assertEquals("/sdk/sources/android-23", fop.getAgnosticAbsPath(sourcesPath));
    }

    private static void verifyPlatform13(IAndroidTarget target, MockFileOp fop) {
        assertEquals(new AndroidVersion(13, null), target.getVersion());
        assertEquals("Android Open Source Project", target.getVendor());
        assertEquals("/sdk/platforms/android-13/", fop.getAgnosticAbsPath(target.getLocation()));
        assertNull(target.getParent());

        assertEquals(ImmutableSet.of(new File("/sdk/platforms/android-13/skins/HVGA"),
                new File("/sdk/platforms/android-13/skins/WVGA800")),
                ImmutableSet.copyOf(target.getSkins()));
        assertEquals(ImmutableList.of("/sdk/platforms/android-13/android.jar"),
                target.getBootClasspath().stream().map(fop::getAgnosticAbsPath)
                        .collect(Collectors.toList()));
        assertEquals(new File("/sdk/build-tools/23.0.2"), target.getBuildToolInfo().getLocation());
        assertEquals(new File("/sdk/platforms/android-13/skins/WXGA"), target.getDefaultSkin());
    }

    private static void verifyPlatform23(IAndroidTarget target, MockFileOp fop) {
        assertEquals(new AndroidVersion(23, null), target.getVersion());
        assertEquals("Android Open Source Project", target.getVendor());
        assertEquals("/sdk/platforms/android-23/", fop.getAgnosticAbsPath(target.getLocation()));
        assertNull(target.getParent());
        assertTrue(Arrays.deepEquals(new File[]{new File("/sdk/platforms/android-23/skins/HVGA"),
                        new File("/sdk/platforms/android-23/skins/WVGA800")},
                target.getSkins()));
        assertEquals(ImmutableList.of("/sdk/platforms/android-23/android.jar"),
                target.getBootClasspath().stream().map(fop::getAgnosticAbsPath)
                        .collect(Collectors.toList()));
        assertEquals(new File("/sdk/build-tools/23.0.2"), target.getBuildToolInfo().getLocation());
        assertEquals(new File("/sdk/platforms/android-23/skins/WVGA800"), target.getDefaultSkin());
    }

    private static void verifyAddon13(IAndroidTarget target, IAndroidTarget platform13,
            MockFileOp fop) {
        assertEquals(new AndroidVersion(13, null), target.getVersion());
        assertEquals("Google Inc.", target.getVendor());
        assertEquals("/sdk/add-ons/addon-google_tv_addon-google-13/",
                fop.getAgnosticAbsPath(target.getLocation()));
        assertEquals(platform13, target.getParent());
        assertEquals(ImmutableSet.of(
                new File("/sdk/platforms/android-13/skins/HVGA"),
                new File("/sdk/add-ons/addon-google_tv_addon-google-13/skins/1080p"),
                new File("/sdk/add-ons/addon-google_tv_addon-google-13/skins/720p-overscan"),
                new File("/sdk/platforms/android-13/skins/WVGA800")),
                ImmutableSet.copyOf(target.getSkins()));
        assertEquals(ImmutableList.of("/sdk/platforms/android-13/android.jar"),
                target.getBootClasspath().stream().map(fop::getAgnosticAbsPath)
                        .collect(Collectors.toList()));
        assertEquals(new File("/sdk/build-tools/23.0.2"), target.getBuildToolInfo().getLocation());
        assertEquals(new File("/sdk/add-ons/addon-google_tv_addon-google-13/skins/720p"),
                target.getDefaultSkin());
    }

    private static void verifyAddon23(IAndroidTarget target, IAndroidTarget platform23,
            MockFileOp fop) {
        assertEquals(new AndroidVersion(23, null), target.getVersion());
        assertEquals("Google Inc.", target.getVendor());
        assertEquals("/sdk/add-ons/addon-google_apis-google-23/",
                fop.getAgnosticAbsPath(target.getLocation()));
        assertEquals(platform23, target.getParent());
        assertEquals(ImmutableSet.of(new File("/sdk/platforms/android-23/skins/HVGA"),
                new File("/sdk/platforms/android-23/skins/WVGA800")),
                ImmutableSet.copyOf(target.getSkins()));
        assertEquals(ImmutableList.of("/sdk/platforms/android-23/android.jar"),
                target.getBootClasspath().stream().map(fop::getAgnosticAbsPath).collect(
                        Collectors.toList()));
        assertEquals(new File("/sdk/build-tools/23.0.2"), target.getBuildToolInfo().getLocation());
        assertEquals(new File("/sdk/platforms/android-23/skins/WVGA800"), target.getDefaultSkin());

        Set<IAndroidTarget.OptionalLibrary> desired
                = Sets.<IAndroidTarget.OptionalLibrary>newHashSet(
                new OptionalLibraryImpl("com.google.android.maps",
                                        new File("/sdk/add-ons/addon-google_apis-google-23/libs/maps.jar"), "",
                                        false),
                new OptionalLibraryImpl("com.android.future.usb.accessory",
                        new File("/sdk/add-ons/addon-google_apis-google-23/libs/usb.jar"), "",
                        false),
                new OptionalLibraryImpl("com.google.android.media.effects",
                        new File("/sdk/add-ons/addon-google_apis-google-23/libs/effects.jar"), "",
                        false));

        Set<IAndroidTarget.OptionalLibrary> libraries = Sets
                .newHashSet(target.getAdditionalLibraries());
        assertEquals(desired, libraries);
    }

    private static void recordBuildTool23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/build-tools/23.0.2/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/generic/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-19E6313A\" type=\"text\">License text\n"
                        + "</license><localPackage path=\"build-tools;23.0.2\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns4:genericDetailsType\"/>"
                        + "<revision><major>23</major><minor>0</minor><micro>2</micro></revision>"
                        + "<display-name>Android SDK Build-Tools 23.0.2</display-name>"
                        + "<uses-license ref=\"license-19E6313A\"/></localPackage>"
                        + "</ns2:sdk-repository>\n");
    }

    private static void recordBuildTool24Preview1(MockFileOp fop) {
        fop.recordExistingFile("/sdk/build-tools/24.0.0-rc1/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/generic/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-19E6313A\" type=\"text\">License text\n"
                        + "</license><localPackage path=\"build-tools;24.0.0-rc1\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns4:genericDetailsType\"/>"
                        + "<revision><major>24</major><minor>0</minor><micro>0</micro><preview>1</preview></revision>"
                        + "<display-name>Android SDK Build-Tools 23.0.2</display-name>"
                        + "<uses-license ref=\"license-19E6313A\"/></localPackage>"
                        + "</ns2:sdk-repository>\n");
    }

    private static void recordBuildTool24(MockFileOp fop) {
        fop.recordExistingFile("/sdk/build-tools/24.0.0/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/generic/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-19E6313A\" type=\"text\">License text\n"
                        + "</license><localPackage path=\"build-tools;24.0.0\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns4:genericDetailsType\"/>"
                        + "<revision><major>24</major><minor>0</minor><micro>0</micro></revision>"
                        + "<display-name>Android SDK Build-Tools 23.0.2</display-name>"
                        + "<uses-license ref=\"license-19E6313A\"/></localPackage>"
                        + "</ns2:sdk-repository>\n");
    }

    private static void recordPlatform13(MockFileOp fop) {
        fop.recordExistingFile("/sdk/platforms/android-13/images/system.img");
        fop.recordExistingFile("/sdk/platforms/android-13/android.jar");
        fop.recordExistingFile("/sdk/platforms/android-13/framework.aidl");
        fop.recordExistingFile("/sdk/platforms/android-13/skins/HVGA/layout");
        fop.recordExistingFile("/sdk/platforms/android-13/skins/dummy.txt");
        fop.recordExistingFile("/sdk/platforms/android-13/skins/WVGA800/layout");
        fop.recordExistingFile("/sdk/platforms/android-13/sdk.properties",
                "sdk.ant.templates.revision=1\n" +
                        "sdk.skin.default=WXGA\n");
        fop.recordExistingFile("/sdk/platforms/android-13/build.prop",
                "ro.build.id=HTJ85B\n"
                        + "ro.build.display.id=sdk-eng 3.2 HTJ85B 140714 test-keys\n"
                        + "ro.build.version.incremental=140714\n"
                        + "ro.build.version.sdk=13\n"
                        + "ro.build.version.codename=REL\n"
                        + "ro.build.version.release=3.2\n"
                        + "ro.build.date=Wed Jul  6 17:51:50 PDT 2011\n"
                        + "ro.build.date.utc=1309999910\n"
                        + "ro.build.type=eng\n"
                        + "ro.build.tags=test-keys\n"
                        + "ro.product.model=sdk\n"
                        + "ro.product.name=sdk\n"
                        + "ro.product.board=\n"
                        + "ro.product.cpu.abi=armeabi\n"
                        + "ro.product.locale.language=ldpi\n"
                        + "ro.wifi.channels=\n"
                        + "ro.board.platform=\n"
                        + "# ro.build.product is obsolete; use ro.product.device\n"
                        + "# Do not try to parse ro.build.description or .fingerprint\n"
                        + "ro.build.description=sdk-eng 3.2 HTJ85B 140714 test-keys\n"
                        + "ro.build.fingerprint=generic/sdk/generic:3.2/HTJ85B/140714:eng/test-keys\n"
                        + "ro.build.characteristics=default\n"
                        + "# end build properties\n"
                        + "#\n"
                        + "# system.prop for generic sdk \n"
                        + "#\n"
                        + "\n"
                        + "rild.libpath=/system/lib/libreference-ril.so\n"
                        + "rild.libargs=-d /dev/ttyS0\n"
                        + "\n"
                        + "#\n"
                        + "# ADDITIONAL_BUILD_PROPERTIES\n"
                        + "#\n"
                        + "ro.config.notification_sound=OnTheHunt.ogg\n"
                        + "ro.config.alarm_alert=Alarm_Classic.ogg\n"
                        + "ro.kernel.android.checkjni=1\n"
                        + "ro.setupwizard.mode=OPTIONAL\n"
                        + "xmpp.auto-presence=true\n"
                        + "ro.config.nocheckin=yes\n"
                        + "net.bt.name=Android\n"
                        + "dalvik.vm.stack-trace-file=/data/anr/traces.txt\n"
                        + "ro.build.user=generic\n"
                        + "ro.build.host=generic\n"
                        + "ro.product.brand=generic\n"
                        + "ro.product.manufacturer=generic\n"
                        + "ro.product.device=generic\n"
                        + "ro.build.product=generic\n");
        fop.recordExistingFile("/sdk/platforms/android-13/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-2A86BE32\" type=\"text\">License Text\n</license>"
                        + "<localPackage path=\"platforms;android-13\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns2:platformDetailsType\"><api-level>13</api-level>"
                        + "<layoutlib api=\"4\"/></type-details><revision><major>1</major>"
                        + "</revision><display-name>API 13: Android 3.2 (Honeycomb)</display-name>"
                        + "<uses-license ref=\"license-2A86BE32\"/><dependencies>"
                        + "<dependency path=\"tools\"><min-revision><major>12</major></min-revision>"
                        + "</dependency></dependencies></localPackage></ns2:sdk-repository>");
    }

    private static void recordSources23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/sources/android-23/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\">"
                        + "<localPackage path=\"sources;android-23\" "
                        + "obsolete=\"false\"><type-details "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns2:sourceDetailsType\"><api-level>23</api-level>"
                        + "</type-details><revision><major>1</major>"
                        + "</revision><display-name>sources 23"
                        + "</display-name></localPackage>"
                        + "</ns2:sdk-repository>\n");
        }

    private static void recordPlatform23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/platforms/android-23/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-9A220565\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage path=\"platforms;android-23\" "
                        + "obsolete=\"false\"><type-details "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns2:platformDetailsType\"><api-level>23</api-level>"
                        + "<layoutlib api=\"15\"/></type-details><revision><major>1</major>"
                        + "</revision><display-name>API 23: Android 6.0 (Marshmallow)"
                        + "</display-name><uses-license ref=\"license-9A220565\"/><dependencies>"
                        + "<dependency path=\"tools\"><min-revision><major>22</major>"
                        + "</min-revision></dependency></dependencies></localPackage>"
                        + "</ns2:sdk-repository>\n");
        fop.recordExistingFile("/sdk/platforms/android-23/android.jar");
        fop.recordExistingFile("/sdk/platforms/android-23/framework.aidl");
        fop.recordExistingFile("/sdk/platforms/android-23/skins/HVGA/layout");
        fop.recordExistingFile("/sdk/platforms/android-23/skins/dummy.txt");
        fop.recordExistingFile("/sdk/platforms/android-23/skins/WVGA800/layout");
        fop.recordExistingFile("/sdk/platforms/android-23/build.prop",
                "# autogenerated by buildinfo.sh\n"
                        + "ro.build.id=MRA44C\n"
                        + "ro.build.display.id=sdk_phone_armv7-eng 6.0 MRA44C 2166767 test-keys\n"
                        + "ro.build.version.incremental=2166767\n"
                        + "ro.build.version.sdk=23\n"
                        + "ro.build.version.preview_sdk=0\n"
                        + "ro.build.version.codename=REL\n"
                        + "ro.build.version.all_codenames=REL\n"
                        + "ro.build.version.release=6.0\n"
                        + "ro.build.version.security_patch=\n"
                        + "ro.build.version.base_os=\n"
                        + "ro.build.date=Thu Aug 13 23:46:41 UTC 2015\n"
                        + "ro.build.date.utc=1439509601\n"
                        + "ro.build.type=eng\n"
                        + "ro.build.tags=test-keys\n"
                        + "ro.build.flavor=sdk_phone_armv7-eng\n"
                        + "ro.product.model=sdk_phone_armv7\n"
                        + "ro.product.name=sdk_phone_armv7\n"
                        + "ro.product.board=\n"
                        + "# ro.product.cpu.abi and ro.product.cpu.abi2 are obsolete,\n"
                        + "# use ro.product.cpu.abilist instead.\n"
                        + "ro.product.cpu.abi=armeabi-v7a\n"
                        + "ro.product.cpu.abi2=armeabi\n"
                        + "ro.product.cpu.abilist=armeabi-v7a,armeabi\n"
                        + "ro.product.cpu.abilist32=armeabi-v7a,armeabi\n"
                        + "ro.product.cpu.abilist64=\n"
                        + "ro.product.locale=en-US\n"
                        + "ro.wifi.channels=\n"
                        + "ro.board.platform=\n"
                        + "# ro.build.product is obsolete; use ro.product.device\n"
                        + "# Do not try to parse description, fingerprint, or thumbprint\n"
                        + "ro.build.description=sdk_phone_armv7-eng 6.0 MRA44C 2166767 test-keys\n"
                        + "ro.build.fingerprint=generic/sdk_phone_armv7/generic:6.0/MRA44C/2166767:eng/test-keys\n"
                        + "ro.build.characteristics=default\n"
                        + "# end build properties\n"
                        + "#\n"
                        + "# from build/target/board/generic/system.prop\n"
                        + "#\n"
                        + "#\n"
                        + "# system.prop for generic sdk\n"
                        + "#\n"
                        + "\n"
                        + "rild.libpath=/system/lib/libreference-ril.so\n"
                        + "rild.libargs=-d /dev/ttyS0\n"
                        + "\n"
                        + "#\n"
                        + "# ADDITIONAL_BUILD_PROPERTIES\n"
                        + "#\n"
                        + "ro.config.notification_sound=OnTheHunt.ogg\n"
                        + "ro.config.alarm_alert=Alarm_Classic.ogg\n"
                        + "persist.sys.dalvik.vm.lib.2=libart\n"
                        + "dalvik.vm.isa.arm.variant=generic\n"
                        + "dalvik.vm.isa.arm.features=default\n"
                        + "ro.kernel.android.checkjni=1\n"
                        + "dalvik.vm.lockprof.threshold=500\n"
                        + "dalvik.vm.usejit=true\n"
                        + "xmpp.auto-presence=true\n"
                        + "ro.config.nocheckin=yes\n"
                        + "net.bt.name=Android\n"
                        + "dalvik.vm.stack-trace-file=/data/anr/traces.txt\n"
                        + "ro.build.user=generic\n"
                        + "ro.build.host=generic\n"
                        + "ro.product.brand=generic\n"
                        + "ro.product.manufacturer=generic\n"
                        + "ro.product.device=generic\n"
                        + "ro.build.product=generic\n");
    }

    private static void recordGoogleApisAddon23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns5:sdk-addon "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-1E15FA4A\" type=\"text\">"
                        + "    Terms and Conditions\n"
                        + "</license>"
                        + "<localPackage path=\"add-ons;addon-google_apis-google-23\" "
                        + "obsolete=\"false\">"
                        + "  <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "                xsi:type=\"ns5:addonDetailsType\">"
                        + "    <api-level>23</api-level>"
                        + "    <vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "    <tag><id>google_apis</id><display>Google APIs</display></tag>"
                        + "    <libraries>"
                        + "      <library name=\"com.google.android.maps\" localJarPath=\"maps.jar\">"
                        + "        <description>API for Google Maps</description>"
                        + "      </library>"
                        + "      <library name=\"com.android.future.usb.accessory\" localJarPath=\"usb.jar\">"
                        + "        <description>API for USB Accessories</description>"
                        + "      </library>"
                        + "      <library name=\"com.google.android.media.effects\" localJarPath=\"effects.jar\">"
                        + "        <description>Collection of video effects</description>"
                        + "      </library>"
                        + "    </libraries>"
                        + "  </type-details>"
                        + "  <revision>"
                        + "    <major>1</major>"
                        + "    <minor>0</minor>"
                        + "    <micro>0</micro>"
                        + "  </revision>"
                        + "  <display-name>Google APIs, Android 23</display-name>"
                        + "  <uses-license ref=\"license-1E15FA4A\"/>"
                        + "</localPackage></ns5:sdk-addon>\n");
    }

    private static void recordGoogleTvAddon13(MockFileOp fop) {
        fop.recordExistingFile("/sdk/add-ons/addon-google_tv_addon-google-13/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<ns5:sdk-addon xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        +
                        "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" " +
                        "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" " +
                        "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">" +
                        "<license id=\"license-A06C75BE\" type=\"text\">Terms and Conditions\n" +
                        "</license><localPackage " +
                        "path=\"add-ons;addon-google_tv_addon-google-13\" obsolete=\"false\">" +
                        "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                        "xsi:type=\"ns5:addonDetailsType\"><api-level>13</api-level>" +
                        "<vendor><id>google</id><display>Google Inc.</display></vendor>" +
                        "<tag><id>google_tv_addon</id><display>Google TV Addon</display></tag>" +
                        "<default-skin>720p</default-skin>" +
                        "</type-details><revision><major>1</major><minor>0</minor>" +
                        "<micro>0</micro></revision>" +
                        "<display-name>Google TV Addon, Android 13</display-name>" +
                        "<uses-license ref=\"license-A06C75BE\"/></localPackage>" +
                        "</ns5:sdk-addon>\n");
        fop.recordExistingFile("/sdk/add-ons/addon-google_tv_addon-google-13/skins/1080p/layout");
        fop.recordExistingFile("/sdk/add-ons/addon-google_tv_addon-google-13/skins/dummy.txt");
        fop.recordExistingFile(
                "/sdk/add-ons/addon-google_tv_addon-google-13/skins/720p-overscan/layout");
        fop.recordExistingFile(
                "/sdk/add-ons/addon-google_tv_addon-google-13/images/x86/system.img");
    }

    private static void recordSysImg13(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-13/default/x86/system.img");
        fop.recordExistingFile("/sdk/system-images/android-13/default/x86/skins/res1/layout");
        fop.recordExistingFile("/sdk/system-images/android-13/default/x86/skins/dummy");
        fop.recordExistingFile("/sdk/system-images/android-13/default/x86/skins/res2/layout");
        fop.recordExistingFile("/sdk/system-images/android-13/default/x86/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-A78C4257\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage path=\"system-images;android-13;default;x86\" "
                        + "obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>13</api-level>"
                        + "<tag><id>default</id><display>Default</display></tag><abi>x86</abi>"
                        + "</type-details><revision><major>5</major></revision>"
                        + "<display-name>Intel x86 Atom System Image</display-name>"
                        + "<uses-license ref=\"license-A78C4257\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordGoogleApisSysImg23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/system.img");
        fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns3:sdk-sys-img "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                        + "</license><localPackage "
                        + "path=\"system-images;android-23;google_apis;x86_64\" "
                        + "obsolete=\"false\"><type-details "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                        + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                        + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                        + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                        + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                        + "</ns3:sdk-sys-img>\n");
    }

    private static void recordLegacyGoogleApis23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/source.properties",
                "Addon.NameDisplay=Google APIs\n"
                        + "Addon.NameId=google_apis\n"
                        + "Addon.VendorDisplay=Google Inc.\n"
                        + "Addon.VendorId=google\n"
                        + "AndroidVersion.ApiLevel=23\n"
                        + "Pkg.Desc=Android + Google APIs\n"
                        + "Pkg.Revision=1\n"
                        + "Pkg.SourceUrl=https\\://dl.google.com/android/repository/addon.xml\n");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                "name=Google APIs\n"
                        + "name-id=google_apis\n"
                        + "vendor=Google Inc.\n"
                        + "vendor-id=google\n"
                        + "description=Android + Google APIs\n"
                        + "\n"
                        + "# version of the Android platform on which this add-on is built.\n"
                        + "api=23\n"
                        + "\n"
                        + "# revision of the add-on\n"
                        + "revision=1\n"
                        + "\n"
                        + "# list of libraries, separated by a semi-colon.\n"
                        + "libraries=com.google.android.maps;com.android.future.usb.accessory;com.google.android.media.effects\n"
                        + "\n"
                        + "# details for each library\n"
                        + "com.google.android.maps=maps.jar;API for Google Maps\n"
                        + "com.android.future.usb.accessory=usb.jar;API for USB Accessories\n"
                        + "com.google.android.media.effects=effects.jar;Collection of video effects\n"
                        + "\n"
                        + "SystemImage.GpuSupport=true\n");
    }

    private static void recordInstalledLegacyGoogleApis23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns5:sdk-addon "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-1E15FA4A\" type=\"text\">"
                        + "    Terms and Conditions\n"
                        + "</license>"
                        + "<localPackage path=\"add-ons;addon-google_apis-google-23\" "
                        + "obsolete=\"false\">"
                        + "  <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "                xsi:type=\"ns5:addonDetailsType\">"
                        + "    <api-level>23</api-level>"
                        + "    <vendor><id>google</id><display>Google Inc.</display></vendor>"
                        + "    <tag><id>google_apis</id><display>Google APIs</display></tag>"
                        + "    <libraries>"
                        + "      <library name=\"com.google.android.maps\">"
                        + "        <description>API for Google Maps</description>"
                        + "      </library>"
                        + "      <library name=\"com.android.future.usb.accessory\">"
                        + "        <description>API for USB Accessories</description>"
                        + "      </library>"
                        + "      <library name=\"com.google.android.media.effects\">"
                        + "        <description>Collection of video effects</description>"
                        + "      </library>"
                        + "    </libraries>"
                        + "  </type-details>"
                        + "  <revision>"
                        + "    <major>1</major>"
                        + "    <minor>0</minor>"
                        + "    <micro>0</micro>"
                        + "  </revision>"
                        + "  <display-name>Google APIs, Android 23</display-name>"
                        + "  <uses-license ref=\"license-1E15FA4A\"/>"
                        + "</localPackage></ns5:sdk-addon>\n");
        fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/manifest.ini",
                "name=Google APIs\n"
                        + "name-id=google_apis\n"
                        + "vendor=Google Inc.\n"
                        + "vendor-id=google\n"
                        + "description=Android + Google APIs\n"
                        + "\n"
                        + "# version of the Android platform on which this add-on is built.\n"
                        + "api=23\n"
                        + "\n"
                        + "# revision of the add-on\n"
                        + "revision=1\n"
                        + "\n"
                        + "# list of libraries, separated by a semi-colon.\n"
                        + "libraries=com.google.android.maps;com.android.future.usb.accessory;com.google.android.media.effects\n"
                        + "\n"
                        + "# details for each library\n"
                        + "com.google.android.maps=maps.jar;API for Google Maps\n"
                        + "com.android.future.usb.accessory=usb.jar;API for USB Accessories\n"
                        + "com.google.android.media.effects=effects.jar;Collection of video effects\n"
                        + "\n"
                        + "SystemImage.GpuSupport=true\n");
    }

    @SuppressWarnings("ConstantConditions")
    public void testBuildTools() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform13(fop);
        recordPlatform23(fop);
        recordBuildTool23(fop);
        recordBuildTool24Preview1(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();

        assertEquals("23.0.2", handler.getLatestBuildTool(progress, false).getRevision().toString());
        assertEquals("24.0.0 rc1", handler.getLatestBuildTool(progress, true).getRevision().toString());
    }

    @SuppressWarnings("ConstantConditions")
    public void testBuildToolsWithPreviewOlderThanStable() throws Exception {
        MockFileOp fop = new MockFileOp();
        recordPlatform13(fop);
        recordPlatform23(fop);
        recordBuildTool23(fop);
        recordBuildTool24Preview1(fop);
        // This test like testBuildTools but also adds in a final version of 24
        recordBuildTool24(fop);

        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();

        assertEquals("24.0.0", handler.getLatestBuildTool(progress, false).getRevision().toString());
        assertEquals("24.0.0", handler.getLatestBuildTool(progress, true).getRevision().toString());
    }

    public void testDuplicatePlatform() throws Exception {
        MockFileOp fop = new MockFileOp();
        File bogus1Location = new File("/sdk", "foo");
        File bogus2Location = new File("/sdk", "bar");
        File real1Location = new File("/sdk", "platforms/android-20");
        File real2Location = new File("/sdk", "platforms/android-19");
        fop.recordExistingFile(new File(bogus1Location, SdkConstants.FN_BUILD_PROP));
        fop.recordExistingFile(new File(bogus2Location, SdkConstants.FN_BUILD_PROP));
        fop.recordExistingFile(new File(real1Location, SdkConstants.FN_BUILD_PROP));
        fop.recordExistingFile(new File(real2Location, SdkConstants.FN_BUILD_PROP));
        LocalPackage bogus1 = new FakePlatformPackage("foo", bogus1Location, 20);
        LocalPackage bogus2 = new FakePlatformPackage("bar", bogus2Location, 20);
        LocalPackage real1 = new FakePlatformPackage(
                DetailsTypes.getPlatformPath(new AndroidVersion(20, null)), real1Location, 20);
        LocalPackage real2 = new FakePlatformPackage("19", real2Location, 19);
        List<LocalPackage> locals = ImmutableList.of(bogus1, bogus2, real1, real2);
        RepositoryPackages packages = new RepositoryPackages(
                Maps.uniqueIndex(locals, RepoPackage::getPath),
                new HashMap<>());
        RepoManager mgr = new FakeRepoManager(packages);
        AndroidSdkHandler handler = new AndroidSdkHandler(new File("/sdk"), fop, mgr);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager targetMgr =
                handler.getAndroidTargetManager(progress);
        Iterator<IAndroidTarget> targetIter = targetMgr.getTargets(progress).iterator();

        IAndroidTarget target19 = targetIter.next();
        assertEquals(new AndroidVersion(19, null),  target19.getVersion());
        IAndroidTarget target20 = targetIter.next();
        assertEquals(new AndroidVersion(20, null), target20.getVersion());
        assertFalse(targetIter.hasNext());

        assertNull(targetMgr.getTargetFromPackage(bogus1, progress));
        assertNull(targetMgr.getTargetFromPackage(bogus2, progress));
        assertEquals(target20, targetMgr.getTargetFromPackage(real1, progress));
        assertEquals(target19, targetMgr.getTargetFromPackage(real2, progress));
    }

    private static class FakePlatformPackage extends FakePackage {
        private final DetailsTypes.PlatformDetailsType mDetails;
        private final File mLocation;

        public FakePlatformPackage(@NonNull String path, @NonNull File location, int apiLevel) {
            super(path, new Revision(1), null);
            mDetails = ((RepoFactory)AndroidSdkHandler.getRepositoryModule().createLatestFactory())
                    .createPlatformDetailsType();
            mDetails.setApiLevel(apiLevel);
            mLocation = location;
        }

        @NonNull
        @Override
        public TypeDetails getTypeDetails() {
            return (TypeDetails)mDetails;
        }

        @NonNull
        @Override
        public File getLocation() {
            return mLocation;
        }
    }
}
