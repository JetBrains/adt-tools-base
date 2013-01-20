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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class ManifestTypoDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ManifestTypoDetector();
    }

    private Set<Issue> mEnabled = new HashSet<Issue>();

    @Override
    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null) {
            @Override
            public boolean isEnabled(@NonNull Issue issue) {
                return super.isEnabled(issue) && mEnabled.contains(issue);
            }
        };
    }

    public void testOk() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
                "No warnings.",
                lintProject(
                        "typo_not_found.xml=>AndroidManifest.xml",
                        "res/values/strings.xml"));
    }

    public void testTypoUsesSdk() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:7: " +
            "Warning: <use-sdk> looks like a typo; did you mean <uses-sdk> ? [ManifestTypo]\n" +
            "    <use-sdk android:minSdkVersion=\"14\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_sdk.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesSdk2() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:7: " +
            "Warning: <user-sdk> looks like a typo; did you mean <uses-sdk> ? [ManifestTypo]\n" +
            "    <user-sdk android:minSdkVersion=\"14\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_sdk2.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesPermission() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:9: " +
            "Warning: <use-permission> looks like a typo; " +
            "did you mean <uses-permission> ? [ManifestTypo]\n" +
            "    <use-permission android:name=\"com.example.helloworld.permission\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_permission.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesPermission2() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:9: " +
            "Warning: <user-permission> looks like a typo; " +
            "did you mean <uses-permission> ? [ManifestTypo]\n" +
            "    <user-permission android:name=\"com.example.helloworld.permission\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_permission2.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesFeature() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:11: " +
            "Warning: <use-feature> looks like a typo; " +
            "did you mean <uses-feature> ? [ManifestTypo]\n" +
            "    <use-feature android:name=\"android.hardware.wifi\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_feature.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesFeature2() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:11: " +
            "Warning: <user-feature> looks like a typo; " +
            "did you mean <uses-feature> ? [ManifestTypo]\n" +
            "    <user-feature android:name=\"android.hardware.wifi\" />\n" +
            "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n" +
            "",

            lintProject(
                    "typo_uses_feature2.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesLibrary() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:16: Warning: <use-library> looks like a typo; did you mean <uses-library> ? [ManifestTypo]\n" +
            "        <use-library android:name=\"com.example.helloworld\" />\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n",

            lintProject(
                    "typo_uses_library.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }

    public void testTypoUsesLibrary2() throws Exception {
        mEnabled = Collections.singleton(ManifestTypoDetector.ISSUE);
        assertEquals(
            "AndroidManifest.xml:16: Warning: <user-library> looks like a typo; did you mean <uses-library> ? [ManifestTypo]\n" +
            "        <user-library android:name=\"com.example.helloworld\" />\n" +
            "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "0 errors, 1 warnings\n",

            lintProject(
                    "typo_uses_library2.xml=>AndroidManifest.xml",
                    "res/values/strings.xml"));
    }
}
