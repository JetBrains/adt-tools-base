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

package com.android.sdklib;

import com.android.sdklib.internal.repository.MockAddonTarget;
import com.android.sdklib.internal.repository.MockPlatformTarget;

import junit.framework.TestCase;

public class AndroidTargetHashTest extends TestCase {

    public final void testGetPlatformHashString() {
        assertEquals("android-10",
                AndroidTargetHash.getPlatformHashString(new AndroidVersion(10, null)));

        assertEquals("android-CODE_NAME",
                AndroidTargetHash.getPlatformHashString(new AndroidVersion(10, "CODE_NAME")));
    }

    public final void testGetAddonHashString() {
        assertEquals("vendor:my-addon:10",
                AndroidTargetHash.getAddonHashString(
                        "vendor",
                        "my-addon",
                        new AndroidVersion(10, null)));
    }

    public final void testGetTargetHashString() {
        MockPlatformTarget t = new MockPlatformTarget(10, 1);
        assertEquals("android-10", AndroidTargetHash.getTargetHashString(t));
        MockAddonTarget a = new MockAddonTarget("my-addon", t, 2);
        assertEquals("vendor 10:my-addon:10", AndroidTargetHash.getTargetHashString(a));
    }

}
