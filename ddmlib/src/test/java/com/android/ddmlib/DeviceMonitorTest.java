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
package com.android.ddmlib;

import junit.framework.TestCase;

import java.util.Map;

public class DeviceMonitorTest extends TestCase {
    public void testDeviceListMonitor() {
        Map<String, IDevice.DeviceState> map = DeviceMonitor.DeviceListMonitorTask
                .parseDeviceListResponse("R32C801BL5K\tdevice\n0079864fd1d150fd\tunauthorized\n");

        assertEquals(IDevice.DeviceState.ONLINE, map.get("R32C801BL5K"));
        assertEquals(IDevice.DeviceState.UNAUTHORIZED, map.get("0079864fd1d150fd"));
    }
}
