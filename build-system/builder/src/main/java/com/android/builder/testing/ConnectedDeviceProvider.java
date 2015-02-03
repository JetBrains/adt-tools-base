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

package com.android.builder.testing;

import com.android.annotations.NonNull;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.DeviceProvider;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * DeviceProvider for locally connected devices. Basically returns the list of devices that
 * are currently connected at the time {@link #init()} is called.
 */
public class ConnectedDeviceProvider extends DeviceProvider {


    @NonNull
    private final File adbLocation;

    @NonNull
    private final List<ConnectedDevice> localDevices = Lists.newArrayList();

    public ConnectedDeviceProvider(@NonNull File adbLocation) {
        this.adbLocation = adbLocation;
    }

    @Override
    @NonNull
    public String getName() {
        return "connected";
    }

    @Override
    @NonNull
    public List<? extends DeviceConnector> getDevices() {
        return localDevices;
    }

    @Override
    public void init() throws DeviceException {
        try {
            AndroidDebugBridge.initIfNeeded(false /*clientSupport*/);

            AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                    adbLocation.getAbsolutePath(), false /*forceNewBridge*/);

            long timeOut = 30000; // 30 sec
            int sleepTime = 1000;
            while (!bridge.hasInitialDeviceList() && timeOut > 0) {
                Thread.sleep(sleepTime);
                timeOut -= sleepTime;
            }

            if (timeOut <= 0 && !bridge.hasInitialDeviceList()) {
                throw new RuntimeException("Timeout getting device list.", null);
            }

            IDevice[] devices = bridge.getDevices();

            if (devices.length == 0) {
                throw new RuntimeException("No connected devices!", null);
            }

            final String androidSerial = System.getenv("ANDROID_SERIAL");
            final Boolean isValidSerial = androidSerial != null && !androidSerial.isEmpty();
            for (IDevice iDevice : devices) {
                if (!isValidSerial || iDevice.getSerialNumber().equals(androidSerial)) {
                    localDevices.add(new ConnectedDevice(iDevice));
                }
            }

            if (isValidSerial && (localDevices.size() == 0)) {
                throw new RuntimeException("Connected device with serial " + androidSerial
                        + " not found!", null);
            }
        } catch (Exception e) {
            throw new DeviceException(e);
        }
    }

    @Override
    public void terminate() throws DeviceException {
        // nothing to be done here.
    }

    @Override
    public int getTimeoutInMs() {
        return 0;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}
