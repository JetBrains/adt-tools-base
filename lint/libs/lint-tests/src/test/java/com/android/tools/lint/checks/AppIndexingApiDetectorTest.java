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

@SuppressWarnings("javadoc")
public class AppIndexingApiDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new AppIndexingApiDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        return true;
    }

    public void testOk() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testDataMissing() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.DATA_MISSING, AppIndexingApiDetector.IssueType.parse("Missing data element"));
        assertEquals(""
                        + "AndroidManifest.xml:15: Error: Missing data element [GoogleAppIndexingDeepLinkError]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "AndroidManifest.xml:15: Warning: Missing deep link [GoogleAppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "1 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNoUrl() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.URL_MISSING, AppIndexingApiDetector.IssueType.parse("Missing URL for the intent filter"));
        assertEquals(""
                        + "AndroidManifest.xml:17: Error: Missing URL for the intent filter [GoogleAppIndexingDeepLinkError]\n"
                        + "                <data />\n"
                        + "                ~~~~~~~~\n"
                        + "AndroidManifest.xml:15: Warning: Missing deep link [GoogleAppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "1 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMimeType() throws Exception {
        assertEquals("AndroidManifest.xml:15: Warning: Missing deep link [GoogleAppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:mimeType=\"mimetype\" /> "
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }


    public void testNoActivity() throws Exception {
        assertEquals(
                ""
                        + "AndroidManifest.xml:5: Warning: App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent-filler. See issue explanation for more details. [GoogleAppIndexingWarning]\n"
                        + "    <application\n"
                        + "    ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNoActionView() throws Exception {
        assertEquals(
                ""
                        + "AndroidManifest.xml:5: Warning: App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent-filler. See issue explanation for more details. [GoogleAppIndexingWarning]\n"
                        + "    <application\n"
                        + "    ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNotBrowsable() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.NOT_BROWSABLE, AppIndexingApiDetector.IssueType.parse("Activity supporting ACTION_VIEW is not set as BROWSABLE"));
        assertEquals(""
                        + "AndroidManifest.xml:25: Warning: Activity supporting ACTION_VIEW is not set as BROWSABLE [GoogleAppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testWrongPathPrefix() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.MISSING_SLASH, AppIndexingApiDetector.IssueType.parse("android:pathPrefix attribute should start with '/', but it is : gizmos"));
        assertEquals(""
                        + "AndroidManifest.xml:19: Error: android:pathPrefix attribute should start with '/', but it is : gizmos [GoogleAppIndexingDeepLinkError]\n"
                        + "                    android:pathPrefix=\"gizmos\" />\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testWrongPort() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.ILLEGAL_NUMBER, AppIndexingApiDetector.IssueType.parse("android:port is not a legal number"));
        assertEquals(""
                        + "AndroidManifest.xml:19: Error: android:port is not a legal number [GoogleAppIndexingDeepLinkError]\n"
                        + "                    android:port=\"ABCD\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:port=\"ABCD\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testSchemeAndHostMissing() throws Exception {
        assertEquals(AppIndexingApiDetector.IssueType.SCHEME_MISSING, AppIndexingApiDetector.IssueType.parse("android:scheme is missing"));
        assertEquals(AppIndexingApiDetector.IssueType.HOST_MISSING, AppIndexingApiDetector.IssueType.parse("android:host is missing"));
        assertEquals(""
                        + "AndroidManifest.xml:17: Error: Missing URL for the intent filter [GoogleAppIndexingDeepLinkError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:17: Error: android:host is missing [GoogleAppIndexingDeepLinkError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:17: Error: android:scheme is missing [GoogleAppIndexingDeepLinkError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:15: Warning: Missing deep link [GoogleAppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "3 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiData() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\" />\n"
                        + "                <data android:host=\"example.com\" />\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiIntent() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiIntentWithError() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:20: Error: android:host is missing [GoogleAppIndexingDeepLinkError]\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNotExported() throws Exception {
        assertEquals("AndroidManifest.xml:10: Error: Activity supporting ACTION_VIEW is not exported [GoogleAppIndexingDeepLinkError]\n"
                        + "        <activity android:exported=\"false\"\n"
                        + "        ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity android:exported=\"false\"\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testOkWithResource() throws Exception {
        assertEquals("No warnings.",
                lintProjectIncrementally(
                        "AndroidManifest.xml",
                        "appindexing_manifest.xml=>AndroidManifest.xml",
                        "res/values/appindexing_strings.xml"));
    }

    public void testWrongWithResource() throws Exception {
        assertEquals("" + "AndroidManifest.xml:18: Error: android:pathPrefix attribute should start with '/', but it is : pathprefix [GoogleAppIndexingDeepLinkError]\n"
                        + "                      android:pathPrefix=\"@string/path_prefix\"\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:19: Error: android:port is not a legal number [GoogleAppIndexingDeepLinkError]\n"
                        + "                      android:port=\"@string/port\"/>\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "AndroidManifest.xml",
                        "appindexing_manifest.xml=>AndroidManifest.xml",
                        "res/values/appindexing_wrong_strings.xml"));
    }

    public void testJavaOk() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestOk.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testNoManifest() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:28: Warning: Missing support for Google App Indexing in the manifest [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                         ~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:36: Warning: Missing support for Google App Indexing in the manifest [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                         ~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestOk.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testNoStartEnd() throws Exception {
        assertEquals(""
                        + "src/com/example/helloworld/AppIndexingApiTest.java:11: Warning: Missing support for Google App Indexing API [GoogleAppIndexingApiWarning]\n"
                        + "public class AppIndexingApiTest extends Activity {\n"
                        + "             ~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestNoStartEnd.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testStartMatch() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:27: Warning: GoogleApiClient mClient is not connected [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                               ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:27: Warning: Missing corresponding AppIndex.AppIndexApi.end method [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                         ~~~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestStartMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testEndMatch() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:33: Warning: GoogleApiClient mClient is not disconnected [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                             ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:33: Warning: Missing corresponding AppIndex.AppIndexApi.start method [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                         ~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestEndMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testViewMatch() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:26: Warning: GoogleApiClient mClient is not connected [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.view(mClient, this, APP_URI, title, WEB_URL, null);\n"
                        + "                              ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:26: Warning: Missing corresponding AppIndex.AppIndexApi.end method [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.view(mClient, this, APP_URI, title, WEB_URL, null);\n"
                        + "                         ~~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestViewMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testViewEndMatch() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:29: Warning: GoogleApiClient mClient is not disconnected [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.viewEnd(mClient, this, APP_URI);\n"
                        + "                                 ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:29: Warning: Missing corresponding AppIndex.AppIndexApi.start method [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.viewEnd(mClient, this, APP_URI);\n"
                        + "                         ~~~~~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestViewEndMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testWrongOrder() throws Exception {
        assertEquals("No warnings.",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestWrongOrder.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

    public void testGoogleApiClientAddApi() throws Exception {
        assertEquals("" + "src/com/example/helloworld/AppIndexingApiTest.java:28: Warning: GoogleApiClient mClient has not added support for App Indexing API [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                               ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:36: Warning: GoogleApiClient mClient has not added support for App Indexing API [GoogleAppIndexingApiWarning]\n"
                        + "    AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                             ~~~~~~~\n"
                        + "0 errors, 2 warnings\n",
                lintProject(
                        "src/com/example/helloworld/AppIndexingApiTestGoogleApiClientAddApi.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/appindexing/AppIndex.java.txt=>src/com/google/android/gms/appindexing/AppIndex.java",
                        "src/com/appindexing/AppIndexApi.java.txt=>src/com/google/android/gms/appindexing/AppIndexApi.java",
                        "src/com/appindexing/GoogleApiClient.java.txt=>src/com/google/android/gms/common/api/GoogleApiClient.java",
                        "src/com/appindexing/Activity.java.txt=>src/com/google/android/app/Activity.java",
                        "src/com/appindexing/Api.java.txt=>src/com/google/android/gms/common/api/Api.java"));
    }

}
