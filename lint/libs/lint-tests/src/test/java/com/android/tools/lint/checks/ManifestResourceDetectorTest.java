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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

public class ManifestResourceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ManifestResourceDetector();
    }

    public void test() throws Exception {
        assertEquals("No warnings.",
                lintProjectIncrementally(
                        "AndroidManifest.xml",
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"foo.bar2\"\n"
                                + "    android:versionCode=\"1\"\n"
                                + "    android:versionName=\"1.0\" >\n"
                                + "\n"
                                + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                + "\n"
                                + "    <application\n"
                                + "        android:icon=\"@drawable/ic_launcher\"\n"
                                + "        android:label=\"@string/app_name\" >\n"
                                + "        <receiver android:enabled=\"@bool/has_honeycomb\" android:name=\"com.google.android.apps.iosched.appwidget.ScheduleWidgetProvider\">\n"
                                + "            <intent-filter>\n"
                                + "                <action android:name=\"android.appwidget.action.APPWIDGET_UPDATE\"/>\n"
                                + "            </intent-filter>\n"
                                + "            <!-- This specifies the widget provider info -->\n"
                                + "            <meta-data android:name=\"android.appwidget.provider\" android:resource=\"@xml/widgetinfo\"/>\n"
                                + "        </receiver>\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>"),
                        xml("res/values/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"app_name\">App Name (Default)</string>\n"
                                + "    <bool name=\"has_honeycomb\">false</bool>"
                                + "</resources>"),
                        xml("res/values-v11/values.xml", ""
                                + "<resources>\n"
                                + "    <bool name=\"has_honeycomb\">true</bool>\n"
                                + "</resources>"),
                        xml("res/values-en-rUS/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"app_name\">App Name (English)</string>\n"
                                + "</resources>"),
                        xml("res/values-xlarge/values.xml", ""
                                + "<resources>\n"
                                + "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n"
                                + "</resources>")
                        ));
    }

    public void testInvalidManifestReference() throws Exception {
        assertEquals(""
                + "AndroidManifest.xml:6: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in mcc [ManifestResource]\n"
                + "    <application android:fullBackupContent=\"@xml/backup\">\n"
                + "                                            ~~~~~~~~~~~\n"
                + "AndroidManifest.xml:8: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in en-rUS [ManifestResource]\n"
                + "            android:process=\"@string/location_process\"\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "AndroidManifest.xml:9: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in watch-v20 [ManifestResource]\n"
                + "            android:enabled=\"@bool/enable_wearable_location_service\">\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "AndroidManifest.xml",
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"test.pkg\">\n"
                                + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                + "\n"
                                + "    <application android:fullBackupContent=\"@xml/backup\">\n"
                                + "        <service\n" // missing stuff here, not important for test
                                + "            android:process=\"@string/location_process\"\n"
                                + "            android:enabled=\"@bool/enable_wearable_location_service\">\n"
                                + "        </service>"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>"),
                        xml("res/values/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"location_process\">Location Process</string>\n"
                                + "</resources>"),
                        xml("res/values/bools.xml", ""
                                + "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
                                + "    <bool name=\"enable_wearable_location_service\">true</bool>\n"
                                + "</resources>"),
                        xml("res/values-en-rUS/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"location_process\">Location Process (English)</string>\n"
                                + "</resources>"),
                        xml("res/values-watch/bools.xml", ""
                                + "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
                                + "    <bool name=\"enable_wearable_location_service\">false</bool>\n"
                                + "</resources>"),
                        xml("res/xml/backup.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<full-backup-content>\n"
                                + "     <include domain=\"file\" path=\"dd\"/>\n"
                                + "     <exclude domain=\"file\" path=\"dd/fo3o.txt\"/>\n"
                                + "     <exclude domain=\"file\" path=\"dd/ss/foo.txt\"/>\n"
                                + "</full-backup-content>"),
                        xml("res/xml-mcc/backup.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<full-backup-content>\n"
                                + "     <include domain=\"file\" path=\"mcc\"/>\n"
                                + "</full-backup-content>")
                ));
    }

    public void testBatchAnalysis() throws Exception {
        assertEquals(""
                + "AndroidManifest.xml:11: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in mcc [ManifestResource]\n"
                + "        android:fullBackupContent=\"@xml/backup\"\n"
                + "                                   ~~~~~~~~~~~\n"
                + "    res/xml-mcc/backup.xml:2: This value will not be used\n"
                + "AndroidManifest.xml:21: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in en-rUS [ManifestResource]\n"
                + "            android:process=\"@string/location_process\"\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values-en-rUS/values.xml:2: This value will not be used\n"
                + "AndroidManifest.xml:22: Error: Resources referenced from the manifest cannot vary by configuration (except for version qualifiers, e.g. -v21.) Found variation in watch [ManifestResource]\n"
                + "            android:enabled=\"@bool/enable_wearable_location_service\">\n"
                + "                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values-watch/bools.xml:2: This value will not be used\n"
                + "3 errors, 0 warnings\n",
                lintProject(
                        xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"foo.bar2\"\n"
                                + "    android:versionCode=\"1\"\n"
                                + "    android:versionName=\"1.0\" >\n"
                                + "\n"
                                + "    <uses-sdk android:minSdkVersion=\"14\" />\n"
                                + "\n"
                                + "    <application\n"
                                + "        android:icon=\"@drawable/ic_launcher\"\n"
                                + "        android:fullBackupContent=\"@xml/backup\"\n"
                                + "        android:label=\"@string/app_name\" >\n"
                                + "        <receiver android:enabled=\"@bool/has_honeycomb\" android:name=\"com.google.android.apps.iosched.appwidget.ScheduleWidgetProvider\">\n"
                                + "            <intent-filter>\n"
                                + "                <action android:name=\"android.appwidget.action.APPWIDGET_UPDATE\"/>\n"
                                + "            </intent-filter>\n"
                                + "            <!-- This specifies the widget provider info -->\n"
                                + "            <meta-data android:name=\"android.appwidget.provider\" android:resource=\"@xml/widgetinfo\"/>\n"
                                + "        </receiver>\n"
                                + "        <service\n"
                                + "            android:process=\"@string/location_process\"\n"
                                + "            android:enabled=\"@bool/enable_wearable_location_service\">\n"
                                + "        </service>"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>"),
                        xml("res/values/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"location_process\">Location Process</string>\n"
                                + "    <string name=\"app_name\">App Name (Default)</string>\n"
                                + "    <bool name=\"has_honeycomb\">false</bool>"
                                + "</resources>"),
                        xml("res/values/bools.xml", ""
                                + "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
                                + "    <bool name=\"enable_wearable_location_service\">true</bool>\n"
                                + "</resources>"),
                        xml("res/values-en-rUS/values.xml", ""
                                + "<resources>\n"
                                + "    <string name=\"location_process\">Location Process (English)</string>\n"
                                + "    <string name=\"app_name\">App Name (English)</string>\n"
                                + "</resources>"),
                        xml("res/values-watch/bools.xml", ""
                                + "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
                                + "    <bool name=\"enable_wearable_location_service\">false</bool>\n"
                                + "</resources>"),
                        xml("res/values-v11/values.xml", ""
                                + "<resources>\n"
                                + "    <bool name=\"has_honeycomb\">true</bool>\n"
                                + "</resources>"),
                        xml("res/values-xlarge/values.xml", ""
                                + "<resources>\n"
                                + "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n"
                                + "</resources>"),
                        xml("res/xml/backup.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<full-backup-content>\n"
                                + "     <include domain=\"file\" path=\"dd\"/>\n"
                                + "     <exclude domain=\"file\" path=\"dd/fo3o.txt\"/>\n"
                                + "     <exclude domain=\"file\" path=\"dd/ss/foo.txt\"/>\n"
                                + "</full-backup-content>"),
                        xml("res/xml-mcc/backup.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<full-backup-content>\n"
                                + "     <include domain=\"file\" path=\"mcc\"/>\n"
                                + "</full-backup-content>")
                ));
    }
}