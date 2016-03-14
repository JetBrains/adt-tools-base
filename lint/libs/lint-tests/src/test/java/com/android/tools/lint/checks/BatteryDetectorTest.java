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

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class BatteryDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new BatteryDetector();
    }

    public void testConnectivityChange() throws Exception {
        assertEquals(""
                + "AndroidManifest.xml:9: Warning: Declaring a broadcastreceiver for android.net.conn.CONNECTIVITY_CHANGE is deprecated for apps targeting N and higher. In general, apps should not rely on this broadcast and instead use JobScheduler or GCMNetworkManager. [BatteryLife]\n"
                + "            <action android:name=\"android.net.conn.CONNECTIVITY_CHANGE\" />\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "AndroidManifest.xml:10: Warning: Use of REQUEST_IGNORE_BATTERY_OPTIMIZATIONS violates the Play Store Content Policy regarding acceptable use cases, as described in http://developer.android.com/training/monitoring-device-state/doze-standby.html [BatteryLife]\n"
                + "            <action android:name=\"android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS\" />\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "AndroidManifest.xml:11: Warning: Use of com.android.camera.NEW_PICTURE is deprecated for all apps starting with the N release independent of the target SDK. Apps should not rely on these broadcasts and instead use JobScheduler [BatteryLife]\n"
                + "            <action android:name=\"com.android.camera.NEW_PICTURE\" />\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "AndroidManifest.xml:12: Warning: Use of android.hardware.action.NEW_PICTURE is deprecated for all apps starting with the N release independent of the target SDK. Apps should not rely on these broadcasts and instead use JobScheduler [BatteryLife]\n"
                + "            <action android:name=\"android.hardware.action.NEW_PICTURE\" />\n"
                + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/BatteryTest.java:15: Warning: Use of REQUEST_IGNORE_BATTERY_OPTIMIZATIONS violates the Play Store Content Policy regarding acceptable use cases, as described in http://developer.android.com/training/monitoring-device-state/doze-standby.html [BatteryLife]\n"
                + "        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);\n"
                + "                                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 5 warnings\n",

                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"com.google.myapplication\">\n"
                                + "\n"
                                + "    <uses-sdk android:targetSdkVersion=\"24\" />\n"
                                + "\n"
                                + "    <receiver android:name=\".MyReceiver\" >\n"
                                + "        <intent-filter>\n"
                                + "            <action android:name=\"android.net.conn.CONNECTIVITY_CHANGE\" />\n"
                                + "            <action android:name=\"android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS\" />\n"
                                // as shown in http://stackoverflow.com/questions/3015448/where-is-android-camera-new-picture-defined
                                + "            <action android:name=\"com.android.camera.NEW_PICTURE\" />\n"
                                + "            <action android:name=\"android.hardware.action.NEW_PICTURE\" />\n"
                                + "            <data android:mimeType=\"image/*\" />"
                                + "        </intent-filter>\n"
                                + "    </receiver>\n"
                                + "\n"
                                + "</manifest>\n"),
                        java("src/test/pkg/BatteryTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.annotation.TargetApi;\n"
                                + "import android.app.Activity;\n"
                                + "import android.content.ActivityNotFoundException;\n"
                                + "import android.content.Intent;\n"
                                + "import android.net.Uri;\n"
                                + "import android.os.Build;\n"
                                + "import android.provider.Settings;\n"
                                + "\n"
                                + "@SuppressWarnings(\"unused\")\n"
                                + "public class BatteryTest extends Activity {\n"
                                + "    @TargetApi(Build.VERSION_CODES.M)\n"
                                + "    public void testNoNo() throws ActivityNotFoundException {\n"
                                + "        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);\n"
                                + "        intent.setData(Uri.parse(\"package:my.pkg\"));\n"
                                + "        startActivity(intent);\n"
                                + "    }\n"
                                + "}\n")));
    }
}
