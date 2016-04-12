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

package com.android.tools.build.test.multidevice;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

/**
 * Utility methods for communicating with the device pool, as defined in buildSrc.
 *
 * This is used by the the AdbPoolDeviceProvider to reserve a device during test execution,
 * and by the Adb test rule to reserve one device over multiple builds for testing instant run
 * and other tasks.
 */
public class DevicePoolClient {

    /** This needs to be kept in sync with the port in integration-test/build.gradle */
    private static final int PORT = 3431;

    public static String reserveDevice(
            @NonNull List<String> serials, @NonNull String displayName) throws IOException {
        if (serials.isEmpty()) {
            throw new IllegalArgumentException("Must supply list of device serials to reserve");
        }
        return request(
                "request " + Joiner.on(',').join(serials) + " " + displayName.replace(' ', '_'));
    }

    public static void returnDevices(
            @NonNull List<String> serials, @NonNull String displayName) throws IOException {
        if (serials.isEmpty()) {
            throw new IllegalArgumentException("Must supply list of device serials to return");
        }
        for (String serial : serials) {
            if (serial.isEmpty()) {
                throw new IllegalArgumentException("Device serial must exist");
            }
            request("return " + serial + " " + displayName.replace(' ', '_'));
        }
    }

    public static void reserveAllDevices(@NonNull String displayName) throws IOException {
        request("requestAll " + displayName.replace(' ', '_'));
    }

    public static void returnAllDevices(@NonNull String displayName) throws IOException {
        request("returnAll " + displayName.replace(' ', '_'));
    }

    private static String request(@NonNull String command) throws IOException {
        try (Socket socket = new Socket((String) null, PORT)) {
            try (BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                try (PrintWriter out = new PrintWriter(socket.getOutputStream())) {
                    out.println(command);
                    out.flush();
                    return input.readLine();
                }
            }
        }
    }
}