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

package com.android.sdklib.internal.repository;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SdkManager;
import com.android.sdklib.SdkManagerTestCase;
import com.android.sdklib.SystemImage;

import java.util.Arrays;

public class LocalSdkParserTest extends SdkManagerTestCase {

    public void testLocalSdkParser_SystemImages() throws Exception {
        SdkManager sdkman = getSdkManager();
        LocalSdkParser parser = new LocalSdkParser();
        MockMonitor monitor = new MockMonitor();

        // By default SdkManagerTestCase creates an SDK with one platform containing
        // a legacy armeabi system image (this is not a separate system image package)

        assertEquals(
                "[Android SDK Tools, revision 0, " +
                 "SDK Platform Android 0.0, API 0, revision 1, " +
                 "Sources for Android SDK, API 0, revision 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(), sdkman, monitor)));

        assertEquals(
                "[SDK Platform Android 0.0, API 0, revision 1, " +
                 "Sources for Android SDK, API 0, revision 0]",
                 Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                         sdkman,
                         LocalSdkParser.PARSE_PLATFORMS | LocalSdkParser.PARSE_SOURCES,
                         monitor)));

        assertEquals(
                "[SDK Platform Android 0.0, API 0, revision 1]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                        sdkman,
                        LocalSdkParser.PARSE_PLATFORMS,
                        monitor)));

        assertEquals(
                "[Sources for Android SDK, API 0, revision 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                        sdkman,
                        LocalSdkParser.PARSE_SOURCES,
                        monitor)));

        assertEquals(
                "[Android SDK Tools, revision 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                        sdkman,
                        LocalSdkParser.PARSE_TOOLS,
                        monitor)));

        // Now add a few "platform subfolders" system images and reload the SDK.
        // This disables the "legacy" mode but it still doesn't create any system image package

        IAndroidTarget t = sdkman.getTargets()[0];
        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_PLATFORM_SUBFOLDER, SdkConstants.ABI_ARMEABI_V7A));
        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_PLATFORM_SUBFOLDER, SdkConstants.ABI_INTEL_ATOM));

        sdkman.reloadSdk(getLog());
        t = sdkman.getTargets()[0];

        assertEquals(
                "[Android SDK Tools, revision 0, " +
                 "SDK Platform Android 0.0, API 0, revision 1, " +
                 "Sources for Android SDK, API 0, revision 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(), sdkman, monitor)));

        // Now add arm + arm v7a images using the new SDK/system-images.
        // The local parser will find the 2 system image packages which are associated
        // with the PlatformTarger in the SdkManager.

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_ARMEABI));
        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_ARMEABI_V7A));

        sdkman.reloadSdk(getLog());

        assertEquals(
                "[Android SDK Tools, revision 0, " +
                 "SDK Platform Android 0.0, API 0, revision 1, " +
                 "ARM EABI v7a System Image, Android API 0, revision 0, " +
                 "ARM EABI System Image, Android API 0, revision 0, " +
                 "Sources for Android SDK, API 0, revision 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(), sdkman, monitor)));

        // Now add an x86 image using the new SDK/system-images.
        // Now this time we do NOT reload the SdkManager instance. Instead the parser
        // will find an unused system image and load it as a "broken package".

        makeSystemImageFolder(new SystemImage(
                sdkman, t, LocationType.IN_SYSTEM_IMAGE, SdkConstants.ABI_INTEL_ATOM));

        assertEquals(
                "[Android SDK Tools, revision 0, " +
                 "SDK Platform Android 0.0, API 0, revision 1, " +
                 "ARM EABI v7a System Image, Android API 0, revision 0, " +
                 "ARM EABI System Image, Android API 0, revision 0, " +
                 "Sources for Android SDK, API 0, revision 0, " +
                 "Broken Intel x86 Atom System Image, API 0]",
                Arrays.toString(parser.parseSdk(sdkman.getLocation(), sdkman, monitor)));

        assertEquals(
                "[Android SDK Tools, revision 0, " +
                 "SDK Platform Android 0.0, API 0, revision 1, " +
                 "ARM EABI v7a System Image, Android API 0, revision 0, " +
                 "ARM EABI System Image, Android API 0, revision 0, " +
                 "Sources for Android SDK, API 0, revision 0, " +
                 "Broken Intel x86 Atom System Image, API 0]",
                 Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                         sdkman,
                         LocalSdkParser.PARSE_ALL,
                         monitor)));

        assertEquals(
                "[SDK Platform Android 0.0, API 0, revision 1, " +
                 "ARM EABI v7a System Image, Android API 0, revision 0, " +
                 "ARM EABI System Image, Android API 0, revision 0, " +
                 "Sources for Android SDK, API 0, revision 0, " +
                 "Broken Intel x86 Atom System Image, API 0]",
                 Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                         sdkman,
                         LocalSdkParser.PARSE_PLATFORMS | // platform also loads system-images
                         LocalSdkParser.PARSE_SOURCES,
                         monitor)));

        assertEquals(
                "[Sources for Android SDK, API 0, revision 0]",
                 Arrays.toString(parser.parseSdk(sdkman.getLocation(),
                         sdkman,
                         LocalSdkParser.PARSE_SOURCES,
                         monitor)));
    }

}

