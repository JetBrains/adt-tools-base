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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.utils.DeviceHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.TimeUnit;


/**
 * Utilities for handling real devices.
 *
 * This is of the form of a test rule so it can cache things and do clean up when devices are used
 * from a central pool.
 */
public class Adb implements TestRule {

    private Adb() {

    }

    public static Adb create() {
        return new Adb();
    }

    @Override
    public Statement apply(@NonNull final Statement base, @NonNull Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        };
    }

    public IDevice getDevice(@NonNull Matcher<AndroidVersion> matcher) {
        IDevice[] devices = getBridge().getDevices();
        for (IDevice device: devices) {
            if (matcher.matches(device.getVersion())) {
                return device;
            }
        }

        // Failed to find, make a pretty error message.
        StringBuilder errorMessage = new StringBuilder("Test requires device that matches ")
                .append(StringDescription.toString(matcher)).append("\nConnected Devices:");
        for (IDevice device: devices) {
            errorMessage.append("    ").append(device).append(": ");
            StringDescription mismatch = new StringDescription();
            matcher.describeMismatch(device.getVersion(), mismatch);
            errorMessage.append(mismatch.toString()).append('\n');
        }

        throw new AssertionError(errorMessage);
    }

    private static Supplier<AndroidDebugBridge> sAdbGetter = Suppliers.memoizeWithExpiration(
            new Supplier<AndroidDebugBridge>() {
                @Override
                public AndroidDebugBridge get() {
                    AndroidDebugBridge.init(false /*clientSupport*/);
                    AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                            SdkHelper.getAdb().getAbsolutePath(), false /*forceNewBridge*/);
                    assertNotNull("Debug bridge", bridge);
                    long timeOut = DeviceHelper.DEFAULT_ADB_TIMEOUT_MSEC;
                    int sleepTime = 1000;
                    while (!bridge.hasInitialDeviceList() && timeOut > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        timeOut -= sleepTime;
                    }

                    if (timeOut <= 0 && !bridge.hasInitialDeviceList()) {
                        throw new RuntimeException("Timeout getting device list.");
                    }
                    return bridge;
                }
            },
            10, TimeUnit.MINUTES);


    private static AndroidDebugBridge getBridge() {
        return sAdbGetter.get();
    }
}
