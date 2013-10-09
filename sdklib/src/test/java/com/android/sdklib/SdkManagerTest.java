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

package com.android.sdklib;


import com.android.SdkConstants;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SdkManager.LayoutlibVersion;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.repository.FullRevision;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Setup will build an SDK Manager local install matching the latest repository-N.xsd. */
public class SdkManagerTest extends SdkManagerTestCase {

    @SuppressWarnings("deprecation")
    public void testSdkManager_LayoutlibVersion() {
        SdkManager sdkman = getSdkManager();
        IAndroidTarget t = sdkman.getTargets()[0];

        assertTrue(t instanceof PlatformTarget);

        LayoutlibVersion lv = ((PlatformTarget) t).getLayoutlibVersion();
        assertNotNull(lv);
        assertEquals(5, lv.getApi());
        assertEquals(2, lv.getRevision());

        assertSame(lv, sdkman.getMaxLayoutlibVersion());
    }

    public void testSdkManager_getBuildTools() {
        SdkManager sdkman = getSdkManager();

        Set<FullRevision> v = sdkman.getBuildTools();
        // Make sure we get a stable set -- hashmap order isn't stable and can't be used in tests.
        if (!(v instanceof TreeSet<?>)) {
            v = Sets.newTreeSet(v);
        }

        assertEquals("[]", getLog().toString());  // no errors in the logger
        assertEquals("[3.0.0, 3.0.1, 18.3.4 rc5]", Arrays.toString(v.toArray()));

        assertEquals(new FullRevision(18, 3, 4, 5),
                     sdkman.getLatestBuildTool().getRevision());

        // Get infos, first one that doesn't exist returns null.
        assertNull(sdkman.getBuildTool(new FullRevision(1)));

        // Now some that exist.
        BuildToolInfo i = sdkman.getBuildTool(new FullRevision(3, 0, 0));
        assertEquals(
                "<BuildToolInfo rev=3.0.0, " +
                "mPath=$SDK/build-tools/3.0.0, " +
                "mPaths={" +
                    "AAPT=$SDK/build-tools/3.0.0/aapt, " +
                    "AIDL=$SDK/build-tools/3.0.0/aidl, " +
                    "DX=$SDK/build-tools/3.0.0/dx, " +
                    "DX_JAR=$SDK/build-tools/3.0.0/lib/dx.jar, " +
                    "LLVM_RS_CC=$SDK/build-tools/3.0.0/llvm-rs-cc, " +
                    "ANDROID_RS=$SDK/build-tools/3.0.0/renderscript/include/, " +
                    "ANDROID_RS_CLANG=$SDK/build-tools/3.0.0/renderscript/clang-include/}>",
                cleanPath(sdkman, i.toString()));

        i = sdkman.getBuildTool(new FullRevision(18, 3, 4, 5));
        assertEquals(
                "<BuildToolInfo rev=18.3.4 rc5, " +
                "mPath=$SDK/build-tools/18.3.4 rc5, " +
                "mPaths={" +
                    "AAPT=$SDK/build-tools/18.3.4 rc5/aapt, " +
                    "AIDL=$SDK/build-tools/18.3.4 rc5/aidl, " +
                    "DX=$SDK/build-tools/18.3.4 rc5/dx, " +
                    "DX_JAR=$SDK/build-tools/18.3.4 rc5/lib/dx.jar, " +
                    "LLVM_RS_CC=$SDK/build-tools/18.3.4 rc5/llvm-rs-cc, " +
                    "ANDROID_RS=$SDK/build-tools/18.3.4 rc5/renderscript/include/, " +
                    "ANDROID_RS_CLANG=$SDK/build-tools/18.3.4 rc5/renderscript/clang-include/, " +
                    "BCC_COMPAT=$SDK/build-tools/18.3.4 rc5/bcc_compat, " +
                    "LD_ARM=$SDK/build-tools/18.3.4 rc5/arm-linux-androideabi-ld, " +
                    "LD_X86=$SDK/build-tools/18.3.4 rc5/i686-linux-android-ld, " +
                    "LD_MIPS=$SDK/build-tools/18.3.4 rc5/mipsel-linux-android-ld" +
                    "}>",
                cleanPath(sdkman, i.toString()));
    }

    public void testSdkManager_SystemImage() throws Exception {
        SdkManager sdkman = getSdkManager();
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        IAndroidTarget t = sdkman.getTargets()[0];

        // By default SdkManagerTestCase creates an SDK with one platform containing
        // a legacy armeabi system image.
        assertEquals(
                "[SystemImage ABI=armeabi, location in platform legacy='$SDK/platforms/v0_0/images']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));

        // Now add a few "platform subfolders" system images and reload the SDK.
        // This disables the "legacy" mode, which means that although the armeabi
        // target from above is present, it is no longer visible.

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_PLATFORM_SUBFOLDER, SdkConstants.ABI_ARMEABI_V7A));
        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_PLATFORM_SUBFOLDER, SdkConstants.ABI_INTEL_ATOM));

        sdkman.reloadSdk(getLog());
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        t = sdkman.getTargets()[0];

        assertEquals(
                "[SystemImage ABI=armeabi-v7a, location in platform subfolder='$SDK/platforms/v0_0/images/armeabi-v7a', " +
                 "SystemImage ABI=x86, location in platform subfolder='$SDK/platforms/v0_0/images/x86']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));

        // Now add arm + arm v7a images using the new SDK/system-images.
        // The x86 one from the platform subfolder is still visible.
        // The armeabi one from the legacy folder is overridden by the new one.
        // The armeabi-v7a one from the platform subfolder is overridden by the new one.

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_ARMEABI));
        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_ARMEABI_V7A));

        sdkman.reloadSdk(getLog());
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        t = sdkman.getTargets()[0];

        assertEquals(
                "[SystemImage ABI=armeabi, location in system image='$SDK/system-images/android-0/armeabi', " +
                 "SystemImage ABI=armeabi-v7a, location in system image='$SDK/system-images/android-0/armeabi-v7a', " +
                 "SystemImage ABI=x86, location in platform subfolder='$SDK/platforms/v0_0/images/x86']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));
    }

    public void testSdkManager_SystemImage_LegacyOverride() throws Exception {
        SdkManager sdkman = getSdkManager();
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        IAndroidTarget t = sdkman.getTargets()[0];

        // By default SdkManagerTestCase creates an SDK with one platform containing
        // a legacy armeabi system image.
        assertEquals(
                "[SystemImage ABI=armeabi, location in platform legacy='$SDK/platforms/v0_0/images']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));

        // Now add a different ABI system image in the new system-images folder.
        // This does not hide the legacy one as long as the ABI type is different
        // (to contrast: having at least one sub-folder in the platform's legacy images folder
        //  will hide the legacy system image. Whereas this does not happen with the new type.)

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_INTEL_ATOM));

        sdkman.reloadSdk(getLog());
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        t = sdkman.getTargets()[0];

        assertEquals(
                "[SystemImage ABI=armeabi, location in platform legacy='$SDK/platforms/v0_0/images', " +
                 "SystemImage ABI=x86, location in system image='$SDK/system-images/android-0/x86']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));

        // Now if we have one new system-image using the same ABI type, it will override the
        // legacy one. This gives us a good path for updates.

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_ARMEABI));


        sdkman.reloadSdk(getLog());
        assertEquals("[PlatformTarget API 0 rev 1]", Arrays.toString(sdkman.getTargets()));
        t = sdkman.getTargets()[0];

        assertEquals(
                "[SystemImage ABI=armeabi, location in system image='$SDK/system-images/android-0/armeabi', " +
                 "SystemImage ABI=x86, location in system image='$SDK/system-images/android-0/x86']",
                cleanPath(sdkman, Arrays.toString(t.getSystemImages())));
    }

    /**
     * Sanitizes the paths used when testing results.
     * <p/>
     * Some methods return absolute paths to the SDK.
     * However the SDK path is actually a randomized location.
     * We clean it by replacing it by the constant '$SDK'.
     * Also all the Windows path separators are converted to unix-like / separators
     * and ".exe" and ".bat" are removed (e.g. for build-tools binaries).
     */
    private String cleanPath(SdkManager sdkman, String string) {
        return string
            .replaceAll(Pattern.quote(sdkman.getLocation()), "\\$SDK")  //$NON-NLS-1$
            .replaceAll("\\.(?:bat|exe)", "")                           //$NON-NLS-1$ //$NON-NLS-2$
            .replace('\\', '/');
    }
}
