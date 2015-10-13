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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.tools.lint.checks.AndroidTvDetector.MISSING_BANNER;
import static com.android.tools.lint.checks.AndroidTvDetector.MISSING_LEANBACK_LAUNCHER;
import static com.android.tools.lint.checks.AndroidTvDetector.MISSING_LEANBACK_SUPPORT;
import static com.android.tools.lint.checks.AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE;
import static com.android.tools.lint.checks.AndroidTvDetector.UNSUPPORTED_TV_HARDWARE;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class AndroidTvDetectorTest extends AbstractCheckTest {

    private Set<Issue> mEnabled = new HashSet<Issue>();

    @Override
    protected Detector getDetector() {
        return new AndroidTvDetector();
    }

    @Override
    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null) {
            @Override
            public boolean isEnabled(@NonNull Issue issue) {
                return super.isEnabled(issue) && mEnabled.contains(issue);
            }
        };
    }

    public void testInvalidNoLeanbackActivity() throws Exception {
        mEnabled = Collections.singleton(MISSING_LEANBACK_LAUNCHER);
        String expected = "AndroidManifest.xml:2: Error: Expecting an activity to have android.intent.category.LEANBACK_LAUNCHER intent filter. [MissingLeanbackLauncher]\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <application>\n"
                + "        <!-- Application contains an activity, but it isn't a leanback launcher activity -->\n"
                + "        <activity android:name=\"com.example.android.test.Activity\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.SEND\"/>\n"
                + "                <category android:name=\"android.intent.category.DEFAULT\"/>\n"
                + "                <data android:mimeType=\"text/plain\"/>\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidLeanbackActivity() throws Exception {
        mEnabled = Collections.singleton(MISSING_LEANBACK_LAUNCHER);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <application>\n"
                + "        <activity android:name=\"com.example.android.TvActivity\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidNoUsesFeatureLeanback() throws Exception {
        mEnabled = Collections.singleton(MISSING_LEANBACK_SUPPORT);
        String expected = "AndroidManifest.xml:2: Error: Expecting <uses-feature android:name=\"android.software.leanback\" android:required=\"false\" /> tag. [MissingLeanbackSupport]\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "^\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <application>\n"
                + "        <activity android:name=\"com.example.android.TvActivity\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidUsesFeatureLeanback() throws Exception {
        mEnabled = Collections.singleton(MISSING_LEANBACK_SUPPORT);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(UNSUPPORTED_TV_HARDWARE);
        String expected = "AndroidManifest.xml:6: Error: Expecting android:required=\"false\" for this hardware feature that may not be supported by all Android TVs. [UnsupportedTvHardware]\n"
                + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                + "                                                    ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-feature\n"
                + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                + "\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(UNSUPPORTED_TV_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature\n"
                + "        android:name=\"android.hardware.touchscreen\"\n"
                + "        android:required=\"false\" />\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidPermissionImpliesNotMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "    <uses-feature android:required=\"false\" android:name=\"android.hardware.telephony\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidPermissionImpliesNotMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "AndroidManifest.xml:5: Warning: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag. [PermissionImpliesUnsupportedHardware]\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidPermissionImpliesMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "AndroidManifest.xml:5: Warning: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag. [PermissionImpliesUnsupportedHardware]\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidPermissionImpliesUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testBannerMissingInApplicationTag() throws Exception {
        mEnabled = Collections.singleton(MISSING_BANNER);
        String expected = "AndroidManifest.xml:5: Warning: Expecting android:banner with the <application> tag or each Leanback launcher activity. [MissingTvBanner]\n"
                + "    <application>\n"
                + "    ^\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <application>\n"
                + "        <activity>\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\"/>\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testBannerInLeanbackLauncherActivity() throws Exception {
        mEnabled = Collections.singleton(MISSING_BANNER);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <application>\n"
                + "        <activity android:banner=\"@drawable/banner\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\"/>\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testBannerInApplicationTag() throws Exception {
        mEnabled = Collections.singleton(MISSING_BANNER);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <application android:banner=\"@drawable/banner\">\n"
                + "        <activity>\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\"/>\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    // Implicit trigger tests

    public void testLeanbackSupportTrigger() throws Exception {
        // Expect some issue to be raised when there is the leanback support trigger.
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String notExpected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-feature android:name=\"android.software.leanback\"/>\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "</manifest>\n"));
        assertNotSame(notExpected, result);

        // Expect no warnings when there is no trigger.
        mEnabled = Collections
                .singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "No warnings.";
        result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testLeanbackLauncherTrigger() throws Exception {
        mEnabled = Collections
                .singleton(MISSING_LEANBACK_SUPPORT);
        String notExpected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <application>\n"
                + "        <activity android:name=\"com.example.android.TvActivity\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LEANBACK_LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n"));
        assertNotSame(notExpected, result);

        // Expect no warnings when there is no trigger.
        mEnabled = Collections.singleton(MISSING_LEANBACK_SUPPORT);
        String expected = "No warnings.";
        result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "</manifest>\n"));
        assertEquals(expected, result);
    }
}
