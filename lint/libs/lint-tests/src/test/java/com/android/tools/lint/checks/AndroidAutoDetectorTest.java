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

import com.android.annotations.NonNull;
import com.android.testutils.SdkTestCase;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetectorTest extends AbstractCheckTest {

    private final SdkTestCase.TestFile mValidAutomotiveDescriptor =
            xml("res/xml/automotive_app_desc.xml", ""
                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<automotiveApp>\n"
                    + "    <uses name=\"media\"/>\n"
                    + "</automotiveApp>\n");

    private SdkTestCase.TestFile mValidAutoAndroidXml = xml(FN_ANDROID_MANIFEST_XML, ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "          xmlns:tools=\"http://schemas.android.com/tools\"\n"
            + "          package=\"com.example.android.uamp\">\n"
            + "\n"
            + "    <application\n"
            + "        android:name=\".UAMPApplication\"\n"
            + "        android:label=\"@string/app_name\"\n"
            + "        android:theme=\"@style/UAmpAppTheme\">\n"
            + "\n"
            + "    <meta-data\n"
            + "        android:name=\"com.google.android.gms.car.application\"\n"
            + "        android:resource=\"@xml/automotive_app_desc\"/>\n"
            + "\n"
            + "        <service\n"
            + "            android:name=\".MusicService\"\n"
            + "            android:exported=\"true\"\n"
            + "            tools:ignore=\"ExportedService\">\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.media.browse.MediaBrowserService\"/>\n"
            + "            </intent-filter>\n"
            + "            <intent-filter>\n"
            + "                <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\"/>\n"
            + "                <category android:name=\"android.intent.category.DEFAULT\"/>\n"
            + "            </intent-filter>\n"
            + "        </service>\n"
            + "\n"
            + "    </application>\n"
            + "\n"
            + "</manifest>\n");

    private Set<Issue> mEnabled = new HashSet<Issue>();

    @Override
    protected Detector getDetector() {
        return new AndroidAutoDetector();
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

    public void testMissingIntentFilter() throws Exception {
        mEnabled = Collections.singleton(
                AndroidAutoDetector.MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE);
        String expected = "AndroidManifest.xml:6: Error: Missing intent-filter for action android.media.browse.MediaBrowserService that is required for android auto support [MissingMediaBrowserServiceIntentFilter]\n"
                + "    <application\n"
                + "    ^\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(
                xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "          package=\"com.example.android.uamp\">\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:name=\".UAMPApplication\"\n"
                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/UAmpAppTheme\"\n"
                        + "        android:banner=\"@drawable/banner_tv\">\n"
                        + "\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.gms.car.application\"\n"
                        + "            android:resource=\"@xml/automotive_app_desc\"/>\n"
                        + "        <service\n"
                        + "            android:name=\".MusicService\"\n"
                        + "            android:exported=\"true\"\n"
                        + "            tools:ignore=\"ExportedService\">\n"
                        + "        </service>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n"),
                mValidAutomotiveDescriptor);

        assertEquals(expected, result);
    }

    public void testInvalidUsesTagInMetadataFile() throws Exception {
        mEnabled = Collections.singleton(AndroidAutoDetector.INVALID_USES_TAG_ISSUE);
        String expected = "" +
                "res/xml/automotive_app_desc.xml:3: Error: Expecting one of media or notification for the name attribute in uses tag. [InvalidUsesTagAttribute]\n"
                + "    <uses name=\"medias\"/>\n"
                + "          ~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(
                mValidAutoAndroidXml,
                xml("res/xml/automotive_app_desc.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<automotiveApp>\n"
                        + "    <uses name=\"medias\"/>\n"
                        + "</automotiveApp>\n"));
        assertEquals(expected, result);
    }

    public void testMissingMediaSearchIntent() throws Exception {
        mEnabled = Collections.singleton(
                AndroidAutoDetector.MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH);
        String expected = "AndroidManifest.xml:6: Error: Missing intent-filter for action android.media.action.MEDIA_PLAY_FROM_SEARCH. [MissingIntentFilterForMediaSearch]\n"
                + "    <application\n"
                + "    ^\n"
                + "1 errors, 0 warnings\n";

        String result = lintProject(
                xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "          package=\"com.example.android.uamp\">\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:name=\".UAMPApplication\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/UAmpAppTheme\">\n"
                        + "\n"
                        + "    <meta-data\n"
                        + "        android:name=\"com.google.android.gms.car.application\"\n"
                        + "        android:resource=\"@xml/automotive_app_desc\"/>\n"
                        + "\n"
                        + "        <service\n"
                        + "            android:name=\".MusicService\"\n"
                        + "            android:exported=\"true\"\n"
                        + "            tools:ignore=\"ExportedService\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.media.browse"
                        + ".MediaBrowserService\"/>\n"
                        + "            </intent-filter>\n"
                        + "        </service>\n"
                        + "\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n"),
                mValidAutomotiveDescriptor);
        assertEquals(expected, result);
    }

    public void testMissingOnPlayFromSearch() throws Exception {
        mEnabled = Collections.singleton(
                AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH);

        String expected = "src/com/example/android/uamp/MSessionCallback.java:5: Error: This class does not override onPlayFromSearch from MediaSession.Callback The method should be overridden and implemented to support Voice search on Android Auto. [MissingOnPlayFromSearch]\n"
                + "public class MSessionCallback extends Callback {\n"
                + "             ~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        String result = lintProject(
                copy("bytecode/.classpath", ".classpath"),
                mValidAutoAndroidXml,
                mValidAutomotiveDescriptor,
                java("src/com/example/android/uamp/MSessionCallback.java", ""
                        + "package com.example.android.uamp;\n"
                        + "\n"
                        + "import android.media.session.MediaSession.Callback;\n"
                        + "\n"
                        + "public class MSessionCallback extends Callback {\n"
                        + "    @Override\n"
                        + "    public void onPlay() {\n"
                        + "        // custom impl\n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }

    public void testValidOnPlayFromSearch() throws Exception {
        mEnabled = Collections.singleton(AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH);

        String expected = "No warnings.";

        String result = lintProject(
                copy("bytecode/.classpath", ".classpath"),
                mValidAutoAndroidXml,
                mValidAutomotiveDescriptor,
                java("src/com/example/android/uamp/MSessionCallback.java", ""
                        + "package com.example.android.uamp;\n"
                        + "\n"
                        + "import android.os.Bundle;\n"
                        + "\n"
                        + "import android.media.session.MediaSession.Callback;\n"
                        + "\n"
                        + "public class MSessionCallback extends Callback {\n"
                        + "    @Override\n"
                        + "    public void onPlayFromSearch(String query, Bundle bundle) {\n"
                        + "        // custom impl\n"
                        + "    }\n"
                        + "}\n"));
        assertEquals(expected, result);
    }
}
