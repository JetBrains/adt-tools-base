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

package com.android.ddmlib;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class AndroidDebugBridgeTest extends TestCase {
    private String mAndroidHome;

    @Override
    protected void setUp() throws Exception {
        mAndroidHome = System.getenv("ANDROID_HOME");
        assertNotNull(
                "This test requires ANDROID_HOME environment variable to point to a valid SDK",
                mAndroidHome);

        AndroidDebugBridge.init(false);
    }

    // https://code.google.com/p/android/issues/detail?id=63170
    public void testCanRecreateAdb() throws IOException {
        File adbPath = new File(mAndroidHome, "platform-tools" + File.separator + "adb");

        AndroidDebugBridge adb = AndroidDebugBridge.createBridge(adbPath.getCanonicalPath(), true);
        assertNotNull(adb);
        AndroidDebugBridge.terminate();

        adb = AndroidDebugBridge.createBridge(adbPath.getCanonicalPath(), true);
        assertNotNull(adb);
        AndroidDebugBridge.terminate();
    }
}
