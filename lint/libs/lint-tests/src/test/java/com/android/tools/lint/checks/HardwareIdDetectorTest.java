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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

public class HardwareIdDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new HardwareIdDetector();
    }

    public void testBluetoothAdapterGetAddressCall() throws Exception {
        assertEquals(
                "src/test/pkg/AppUtils.java:8: Warning: Using getAddress to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return adapter.getAddress();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        copy("bytecode/.classpath", ".classpath"),
                        java("src/test/pkg/AppUtils.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.bluetooth.BluetoothAdapter;\n"
                                + "\n"
                                + "public class AppUtils {\n"
                                + "    public String getBAddress() {\n"
                                + "        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();\n"
                                + "        return adapter.getAddress();\n"
                                + "    }\n"
                                + "}\n")));
    }

    public void testGetAddressCallInCatchBlock() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(
                        copy("bytecode/.classpath", ".classpath"),
                        java("src/com/google/android/gms/common/GooglePlayServicesNotAvailableException.java", ""
                                + "package com.google.android.gms.common;\n"
                                + "public class GooglePlayServicesNotAvailableException extends Exception {\n"
                                + "}\n"),
                        java("src/com/google/android/gms/dummy/GmsDummyClient.java", ""
                                + "package com.google.android.gms.dummy;"
                                + "import com.google.android.gms.common.GooglePlayServicesNotAvailableException;"
                                + "public class GmsDummyClient {\n"
                                + "    public static String getId() throws GooglePlayServicesNotAvailableException {\n"
                                + "        return \"dummyId\";\n"
                                + "    }\n"
                                + "}\n"),
                        java("src/test/pkg/AppUtils.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.bluetooth.BluetoothAdapter;\n"
                                + "import android.content.Context;\n"
                                + "\n"
                                + "import com.google.android.gms.dummy.GmsDummyClient;\n"
                                + "import com.google.android.gms.common.GooglePlayServicesNotAvailableException;\n"
                                + "\n"
                                + "import java.io.IOException;\n"
                                + "\n"
                                + "public class AppUtils {\n"
                                + "\n"
                                + "    public String getAdvertisingId(Context context) {\n"
                                + "        try {\n"
                                + "            return GmsDummyClient.getId();\n"
                                + "        } catch (RuntimeException | GooglePlayServicesNotAvailableException e) {\n"
                                + "            // not available so get one of the ids.\n"
                                + "            return BluetoothAdapter.getDefaultAdapter().getAddress();\n"
                                + "        }\n"
                                + "    }\n"
                                + "}")));
    }

    public void testGetAndroidId() throws Exception {
        assertEquals(
                "src/test/pkg/AppUtils.java:9: Warning: Using getString to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return Settings.Secure.getString(context.getContentResolver(), androidId);\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        copy("bytecode/.classpath", ".classpath"),
                        java("src/test/pkg/AppUtils.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Context;\n"
                                + "import android.provider.Settings;\n"
                                + "\n"
                                + "public class AppUtils {\n"
                                + "    public String getAndroidId(Context context) {\n"
                                + "        String androidId = Settings.Secure.ANDROID_ID;\n"
                                + "        return Settings.Secure.getString(context.getContentResolver(), androidId);\n"
                                + "    }\n"
                                + "}\n")));
    }

    public void testWifiInfoGetMacAddress() throws Exception {
        assertEquals(
                "src/test/pkg/AppUtils.java:8: Warning: Using getMacAddress to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return info.getMacAddress();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        copy("bytecode/.classpath", ".classpath"),
                        java("src/test/pkg/AppUtils.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Context;\n"
                                + "import android.net.wifi.WifiInfo;\n"
                                + "\n"
                                + "public class AppUtils {\n"
                                + "    public String getMacAddress(WifiInfo info) {\n"
                                + "        return info.getMacAddress();\n"
                                + "    }\n"
                                + "}\n")));
    }

    public void testTelephoneManagerIdentifierCalls() throws Exception {
        assertEquals(
                "src/test/pkg/AppUtils.java:8: Warning: Using getDeviceId to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return info.getDeviceId();\n"
                        + "               ~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/AppUtils.java:11: Warning: Using getLine1Number to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return info.getLine1Number();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/AppUtils.java:14: Warning: Using getSimSerialNumber to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return info.getSimSerialNumber();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/AppUtils.java:17: Warning: Using getSubscriberId to get device identifiers is not recommended. [HardwareIds]\n"
                        + "        return info.getSubscriberId();\n"
                        + "               ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n",
                lintProject(
                        copy("bytecode/.classpath", ".classpath"),
                        java("src/test/pkg/AppUtils.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.content.Context;\n"
                                + "import android.telephony.TelephonyManager;\n"
                                + "\n"
                                + "public class AppUtils {\n"
                                + "    public String getDeviceId(TelephonyManager info) {\n"
                                + "        return info.getDeviceId();\n"
                                + "    }\n"
                                + "    public String getLine1Number(TelephonyManager info) {\n"
                                + "        return info.getLine1Number();\n"
                                + "    }\n"
                                + "    public String getSerial(TelephonyManager info) {\n"
                                + "        return info.getSimSerialNumber();\n"
                                + "    }\n"
                                + "    public String getSubscriberId(TelephonyManager info) {\n"
                                + "        return info.getSubscriberId();\n"
                                + "    }\n"
                                + "}\n")));
    }

}